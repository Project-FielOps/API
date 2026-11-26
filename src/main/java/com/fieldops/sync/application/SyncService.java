package com.fieldops.sync.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.inspection.application.InspectionService;
import com.fieldops.inspection.domain.Inspection;
import com.fieldops.inspection.domain.InspectionResponse;
import com.fieldops.inspection.dto.AnswerResponse;
import com.fieldops.inspection.dto.AnswerUpsertRequest;
import com.fieldops.inspection.mapper.InspectionMapper;
import com.fieldops.inspection.repository.InspectionResponseRepository;
import com.fieldops.review.application.NonConformityService;
import com.fieldops.review.domain.NonConformity;
import com.fieldops.review.dto.CreateNonConformityRequest;
import com.fieldops.review.mapper.ReviewMapper;
import com.fieldops.shared.exception.BusinessRuleException;
import com.fieldops.shared.exception.ConflictException;
import com.fieldops.shared.exception.ForbiddenOperationException;
import com.fieldops.shared.exception.NotFoundException;
import com.fieldops.sync.domain.SyncChange;
import com.fieldops.sync.domain.SyncOperation;
import com.fieldops.sync.domain.SyncOperationStatus;
import com.fieldops.sync.dto.*;
import com.fieldops.sync.repository.SyncChangeRepository;
import com.fieldops.sync.repository.SyncOperationRepository;
import com.fieldops.user.domain.User;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;

/**
 * Sincronização do aplicativo mobile (offline-first).
 *
 * <p>Cada operação do lote roda na sua própria transação: aplicar a mudança e registrar o
 * {@code operationId} acontecem juntos (ou nenhum dos dois). Uma operação recusada não desfaz as
 * demais do lote, e o reenvio de uma operação já registrada devolve {@code ALREADY_APPLIED} com o
 * mesmo resultado — sem reaplicar nada no banco principal.
 */
@Service
public class SyncService {

    private static final int MAX_PAYLOAD_BYTES = 1_048_576;
    private static final int MAX_OPERATION_PAYLOAD_BYTES = 262_144;
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "ANSWER_UPSERT", "INSPECTION_START", "INSPECTION_SUBMIT", "INSPECTION_REOPEN_CORRECTION",
            "NON_CONFORMITY_CREATE");
    private static final Set<String> CONFLICT_CODES = Set.of(
            "INSPECTION_LOCKED", "INVALID_TRANSITION", "VERSION_CONFLICT");

    private final ObjectMapper objectMapper;
    private final InspectionService inspectionService;
    private final InspectionMapper inspectionMapper;
    private final InspectionResponseRepository responseRepository;
    private final NonConformityService nonConformityService;
    private final ReviewMapper reviewMapper;
    private final SyncOperationRepository operationRepository;
    private final SyncChangeRepository changeRepository;
    private final Validator validator;
    private final TransactionTemplate perOperation;

    public SyncService(ObjectMapper objectMapper, InspectionService inspectionService, InspectionMapper inspectionMapper,
                       InspectionResponseRepository responseRepository, NonConformityService nonConformityService,
                       ReviewMapper reviewMapper, SyncOperationRepository operationRepository,
                       SyncChangeRepository changeRepository, Validator validator,
                       PlatformTransactionManager transactionManager) {
        this.objectMapper = objectMapper;
        this.inspectionService = inspectionService;
        this.inspectionMapper = inspectionMapper;
        this.responseRepository = responseRepository;
        this.nonConformityService = nonConformityService;
        this.reviewMapper = reviewMapper;
        this.operationRepository = operationRepository;
        this.changeRepository = changeRepository;
        this.validator = validator;
        this.perOperation = new TransactionTemplate(transactionManager);
        this.perOperation.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

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
        List<SyncChange> changes = changeRepository
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
                return record(request, deviceId, actor, SyncOperationStatus.DEPENDENCY_FAILED, "DEPENDENCY_FAILED");
            }
        }
        if (!SUPPORTED_TYPES.contains(request.type())) {
            return record(request, deviceId, actor, SyncOperationStatus.REJECTED, "UNSUPPORTED_OPERATION");
        }
        if (request.payload().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_OPERATION_PAYLOAD_BYTES) {
            return record(request, deviceId, actor, SyncOperationStatus.REJECTED, "OPERATION_PAYLOAD_TOO_LARGE");
        }
        if (request.effectiveAttempt() > 5) {
            return record(request, deviceId, actor, SyncOperationStatus.REJECTED, "RETRY_LIMIT_EXCEEDED");
        }
        try {
            return perOperation.execute(tx -> {
                Object payload = execute(request, actor);
                return persistResult(request, deviceId, actor, SyncOperationStatus.APPLIED, null, payload);
            });
        } catch (BusinessRuleException ex) {
            SyncOperationStatus status = CONFLICT_CODES.contains(ex.getRuleCode()) || ex.getRuleCode().contains("CONFLICT")
                    ? SyncOperationStatus.CONFLICT : SyncOperationStatus.REJECTED;
            return record(request, deviceId, actor, status, ex.getRuleCode());
        } catch (ConflictException ex) {
            return record(request, deviceId, actor, SyncOperationStatus.CONFLICT, "INVALID_STATE");
        } catch (ForbiddenOperationException ex) {
            return record(request, deviceId, actor, SyncOperationStatus.REJECTED, "FORBIDDEN_SCOPE");
        } catch (NotFoundException ex) {
            return record(request, deviceId, actor, SyncOperationStatus.REJECTED, "OBJECT_NOT_FOUND");
        } catch (InvalidPayloadException ex) {
            return record(request, deviceId, actor, SyncOperationStatus.REJECTED, ex.code);
        } catch (DataIntegrityViolationException ex) {
            // Duas requisições com o mesmo operationId ao mesmo tempo: a outra venceu.
            return operationRepository.findByUserIdAndDeviceIdAndOperationId(actor.getId(), deviceId, request.operationId())
                    .map(op -> toResult(op, SyncOperationStatus.ALREADY_APPLIED))
                    .orElseThrow(() -> ex);
        }
    }

    private Object execute(SyncOperationRequest request, User actor) {
        UUID inspectionId = request.entityId();
        if (request.baseVersion() != null && request.type().startsWith("INSPECTION_")) {
            Inspection inspection = inspectionService.getAuthorized(inspectionId, actor);
            if (!Objects.equals(request.baseVersion(), inspection.getVersion())) {
                throw new BusinessRuleException("VERSION_CONFLICT", "Inspection version differs from the client version");
            }
        }
        return switch (request.type()) {
            case "ANSWER_UPSERT" -> executeAnswer(request, actor);
            case "INSPECTION_START" -> inspectionState(inspectionService.start(inspectionId, actor));
            case "INSPECTION_SUBMIT" -> inspectionState(inspectionService.submit(inspectionId, actor));
            case "INSPECTION_REOPEN_CORRECTION" -> inspectionState(inspectionService.reopenCorrection(inspectionId, actor));
            case "NON_CONFORMITY_CREATE" -> executeNonConformity(request, actor);
            default -> throw new BusinessRuleException("UNSUPPORTED_OPERATION", "Unsupported sync operation");
        };
    }

    /** Estado oficial devolvido ao aparelho depois de uma transição — nunca a entidade JPA. */
    private Map<String, Object> inspectionState(Inspection inspection) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("id", inspection.getId());
        state.put("status", inspection.getStatus());
        state.put("version", inspection.getVersion());
        return state;
    }

    private AnswerResponse executeAnswer(SyncOperationRequest request, User actor) {
        AnswerUpsertRequest answer = readPayload(request, AnswerUpsertRequest.class);
        if (request.baseVersion() != null) {
            Optional<InspectionResponse> current = responseRepository.findByInspectionItemId(answer.inspectionItemId());
            if (current.isPresent() && !Objects.equals(request.baseVersion(), current.get().getVersion())) {
                throw new BusinessRuleException("VERSION_CONFLICT", "Answer version differs from the client version");
            }
        }
        InspectionResponse saved = inspectionService.upsertAnswer(request.entityId(), answer, actor);
        return inspectionMapper.toAnswerResponse(saved);
    }

    private Object executeNonConformity(SyncOperationRequest request, User actor) {
        CreateNonConformityRequest body = readPayload(request, CreateNonConformityRequest.class);
        Inspection inspection = inspectionService.getById(request.entityId());
        if (!inspection.getTechnician().getId().equals(actor.getId())) {
            throw new ForbiddenOperationException("Only the assigned technician can register non-conformities offline");
        }
        NonConformity nc = nonConformityService.create(request.entityId(), body, actor, request.operationId());
        return reviewMapper.toResponse(nc);
    }

    private <T> T readPayload(SyncOperationRequest request, Class<T> type) {
        T value;
        try {
            value = objectMapper.treeToValue(request.payload(), type);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new InvalidPayloadException("INVALID_OPERATION_PAYLOAD");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            throw new InvalidPayloadException("REQUIRED_FIELD_MISSING");
        }
        return value;
    }

    /** Registra um resultado sem efeito no domínio (recusa, conflito, dependência). */
    private SyncOperationResult record(SyncOperationRequest request, UUID deviceId, User actor,
                                       SyncOperationStatus status, String code) {
        try {
            return perOperation.execute(tx -> persistResult(request, deviceId, actor, status, code, null));
        } catch (DataIntegrityViolationException ex) {
            return operationRepository.findByUserIdAndDeviceIdAndOperationId(actor.getId(), deviceId, request.operationId())
                    .map(op -> toResult(op, SyncOperationStatus.ALREADY_APPLIED))
                    .orElseThrow(() -> ex);
        }
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
        operation = operationRepository.saveAndFlush(operation);
        // Transições de inspeção entram no feed pelo InspectionChangeFeed; aqui só respostas e NCs.
        if (status == SyncOperationStatus.APPLIED && !request.type().startsWith("INSPECTION_")) {
            SyncChange change = new SyncChange();
            change.setUser(actor);
            change.setEntityType(request.type().startsWith("ANSWER") ? "InspectionResponse" : "NonConformity");
            change.setEntityId(changeEntityId(request, payload));
            change.setOperationId(request.operationId());
            change.setPayload(write(payload));
            changeRepository.save(change);
        }
        return new SyncOperationResult(request.operationId(), status, code, read(operation.getResultPayload()));
    }

    private UUID changeEntityId(SyncOperationRequest request, Object payload) {
        if (payload instanceof AnswerResponse answer) return answer.id();
        if (payload instanceof com.fieldops.review.dto.NonConformityResponse nc) return nc.id();
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

    private static final class InvalidPayloadException extends RuntimeException {
        private final String code;

        private InvalidPayloadException(String code) {
            super(code);
            this.code = code;
        }
    }
}
