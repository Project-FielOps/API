package com.fieldops.sync.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.auth.security.FieldOpsUserDetails;
import com.fieldops.inspection.application.InspectionService;
import com.fieldops.inspection.domain.Inspection;
import com.fieldops.inspection.domain.InspectionResponse;
import com.fieldops.inspection.dto.AnswerResponse;
import com.fieldops.inspection.dto.AnswerUpsertRequest;
import com.fieldops.inspection.mapper.InspectionMapper;
import com.fieldops.inspection.repository.InspectionResponseRepository;
import com.fieldops.shared.exception.BusinessRuleException;
import com.fieldops.shared.exception.NotFoundException;
import com.fieldops.sync.domain.SyncChange;
import com.fieldops.sync.domain.SyncOperation;
import com.fieldops.sync.domain.SyncOperationStatus;
import com.fieldops.sync.dto.*;
import com.fieldops.sync.repository.SyncChangeRepository;
import com.fieldops.sync.repository.SyncOperationRepository;
import com.fieldops.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SyncService {

    private static final int MAX_OPERATIONS = 50;
    private static final int MAX_PAYLOAD_BYTES = 1_048_576;
    private static final int MAX_OPERATION_PAYLOAD_BYTES = 262_144;
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "ANSWER_UPSERT", "INSPECTION_START", "INSPECTION_SUBMIT", "INSPECTION_REOPEN_CORRECTION");

    private final ObjectMapper objectMapper;
    private final InspectionService inspectionService;
    private final InspectionMapper inspectionMapper;
    private final InspectionResponseRepository responseRepository;
    private final SyncOperationRepository operationRepository;
    private final SyncChangeRepository changeRepository;

    @Transactional
    public SyncPushResponse push(SyncPushRequest request, User actor, long contentLength) {
        if (contentLength > MAX_PAYLOAD_BYTES) {
            throw new BusinessRuleException("SYNC_PAYLOAD_TOO_LARGE", "Sync payload must not exceed 1 MB");
        }
        Map<UUID, SyncOperationStatus> statuses = new HashMap<>();
        List<SyncOperationResult> results = new ArrayList<>();
        for (SyncOperationRequest operation : request.operations()) {
            SyncOperationResult result = process(operation, request.deviceId(), actor, statuses);
            statuses.put(operation.operationId(), result.status());
            results.add(result);
        }
        return new SyncPushResponse(results);
    }

    @Transactional(readOnly = true)
    public SyncPullResponse pull(String cursor, int requestedLimit, User actor) {
        long parsedCursor = parseCursor(cursor);
        int limit = requestedLimit <= 0 ? 100 : requestedLimit;
        if (limit > 100) {
            throw new BusinessRuleException("SYNC_PAGE_TOO_LARGE", "Sync pull limit must not exceed 100");
        }
        List<com.fieldops.sync.domain.SyncChange> changes = changeRepository
                .findByUserIdAndSequenceGreaterThanOrderBySequenceAsc(actor.getId(), parsedCursor, PageRequest.of(0, limit));
        List<SyncChangeResponse> response = changes.stream().map(this::toChangeResponse).toList();
        long next = response.isEmpty() ? parsedCursor : response.get(response.size() - 1).sequence();
        return new SyncPullResponse(response, Long.toString(next));
    }

    private SyncOperationResult process(SyncOperationRequest request, UUID deviceId, User actor,
                                        Map<UUID, SyncOperationStatus> batchStatuses) {
        Optional<SyncOperation> existing = operationRepository
                .findByUserIdAndDeviceIdAndOperationId(actor.getId(), deviceId, request.operationId());
        if (existing.isPresent()) {
            return toResult(existing.get(), SyncOperationStatus.ALREADY_APPLIED);
        }
        for (UUID dependency : request.effectiveDependencies()) {
            SyncOperationStatus status = batchStatuses.get(dependency);
            if (status == null) {
                status = operationRepository.findByUserIdAndDeviceIdAndOperationId(actor.getId(), deviceId, dependency)
                        .map(SyncOperation::getStatus).orElse(null);
            }
            if (status != SyncOperationStatus.APPLIED && status != SyncOperationStatus.ALREADY_APPLIED) {
                return persistResult(request, deviceId, actor, SyncOperationStatus.DEPENDENCY_FAILED,
                        "DEPENDENCY_FAILED", null);
            }
        }
        if (!SUPPORTED_TYPES.contains(request.type())) {
            return persistResult(request, deviceId, actor, SyncOperationStatus.REJECTED,
                    "UNSUPPORTED_OPERATION", null);
        }
        if (request.payload().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_OPERATION_PAYLOAD_BYTES) {
            return persistResult(request, deviceId, actor, SyncOperationStatus.REJECTED,
                    "OPERATION_PAYLOAD_TOO_LARGE", null);
        }
        if (request.effectiveAttempt() > 5) {
            return persistResult(request, deviceId, actor, SyncOperationStatus.REJECTED,
                    "RETRY_LIMIT_EXCEEDED", null);
        }
        try {
            Object payload = execute(request, actor);
            return persistResult(request, deviceId, actor, SyncOperationStatus.APPLIED, null, payload);
        } catch (BusinessRuleException ex) {
            SyncOperationStatus status = ex.getRuleCode().contains("CONFLICT") ||
                    ex.getRuleCode().equals("INSPECTION_LOCKED") || ex.getRuleCode().equals("INVALID_TRANSITION")
                    ? SyncOperationStatus.CONFLICT : SyncOperationStatus.REJECTED;
            return persistResult(request, deviceId, actor, status, ex.getRuleCode(), null);
        } catch (NotFoundException ex) {
            return persistResult(request, deviceId, actor, SyncOperationStatus.REJECTED, "OBJECT_NOT_FOUND", null);
        } catch (JsonProcessingException ex) {
            return persistResult(request, deviceId, actor, SyncOperationStatus.REJECTED, "INVALID_OPERATION_PAYLOAD", null);
        }
    }

    private Object execute(SyncOperationRequest request, User actor) throws JsonProcessingException {
        UUID inspectionId = request.entityId();
        if (request.baseVersion() != null) {
            Inspection inspection = inspectionService.getAuthorized(inspectionId, actor);
            if (request.type().startsWith("INSPECTION_") && !Objects.equals(request.baseVersion(), inspection.getVersion())) {
                throw new BusinessRuleException("VERSION_CONFLICT", "Inspection version differs from the client version");
            }
        }
        return switch (request.type()) {
            case "ANSWER_UPSERT" -> executeAnswer(request, actor);
            case "INSPECTION_START" -> inspectionService.start(inspectionId, actor);
            case "INSPECTION_SUBMIT" -> inspectionService.submit(inspectionId, actor);
            case "INSPECTION_REOPEN_CORRECTION" -> inspectionService.reopenCorrection(inspectionId, actor);
            default -> throw new BusinessRuleException("UNSUPPORTED_OPERATION", "Unsupported sync operation");
        };
    }

    private AnswerResponse executeAnswer(SyncOperationRequest request, User actor) throws JsonProcessingException {
        AnswerUpsertRequest answer = objectMapper.treeToValue(request.payload(), AnswerUpsertRequest.class);
        if (request.baseVersion() != null) {
            Optional<InspectionResponse> current = responseRepository.findByInspectionItemId(answer.inspectionItemId());
            if (current.isPresent() && !Objects.equals(request.baseVersion(), current.get().getVersion())) {
                throw new BusinessRuleException("VERSION_CONFLICT", "Answer version differs from the client version");
            }
        }
        InspectionResponse saved = inspectionService.upsertAnswer(request.entityId(), answer, actor);
        return inspectionMapper.toAnswerResponse(saved);
    }

    private SyncOperationResult persistResult(SyncOperationRequest request, UUID deviceId, User actor,
                                              SyncOperationStatus status, String code, Object payload) {
        SyncOperation operation = new SyncOperation();
        operation.setOperationId(request.operationId());
        operation.setUser(actor);
        operation.setDeviceId(deviceId);
        operation.setType(request.type());
        operation.setEntityId(request.entityId());
        operation.setBaseVersion(request.baseVersion());
        operation.setDependsOn(write(request.effectiveDependencies()));
        operation.setPayload(write(request.payload()));
        operation.setStatus(status);
        operation.setResultCode(code);
        operation.setResultPayload(write(payload));
        operation.setAttempts(request.effectiveAttempt());
        operation.setProcessedAt(Instant.now());
        operation = operationRepository.save(operation);
        if (status == SyncOperationStatus.APPLIED) {
            SyncChange change = new SyncChange();
            change.setUser(actor);
            change.setEntityType(request.type().startsWith("ANSWER") ? "InspectionResponse" : "Inspection");
            change.setEntityId(changeEntityId(request, payload));
            change.setOperationId(request.operationId());
            change.setPayload(write(payload));
            changeRepository.save(change);
        }
        return new SyncOperationResult(request.operationId(), status, code, payload);
    }

    private UUID changeEntityId(SyncOperationRequest request, Object payload) {
        if (request.type().equals("ANSWER_UPSERT") && payload instanceof AnswerResponse answer) {
            return answer.id();
        }
        return request.entityId();
    }

    private SyncOperationResult toResult(SyncOperation operation, SyncOperationStatus status) {
        return new SyncOperationResult(operation.getOperationId(), status, operation.getResultCode(),
                read(operation.getResultPayload()));
    }

    private SyncChangeResponse toChangeResponse(SyncChange change) {
        return new SyncChangeResponse(change.getSequence(), change.getEntityType(), change.getEntityId(),
                change.getOperationId(), read(change.getPayload()), change.getCreatedAt());
    }

    private long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            long value = Long.parseLong(cursor);
            if (value < 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) {
            throw new BusinessRuleException("INVALID_SYNC_CURSOR", "Sync cursor must be a non-negative number");
        }
    }

    private String write(Object value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Could not serialize sync value", ex); }
    }

    private JsonNode read(String value) {
        if (value == null) return null;
        try { return objectMapper.readTree(value); }
        catch (JsonProcessingException ex) { return objectMapper.createObjectNode(); }
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpiredOperations() {
        operationRepository.deleteByProcessedAtBefore(Instant.now().minusSeconds(90L * 24 * 60 * 60));
    }
}
