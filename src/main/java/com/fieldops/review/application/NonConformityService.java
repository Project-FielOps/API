package com.fieldops.review.application;

import com.fieldops.audit.application.AuditService;
import com.fieldops.inspection.application.InspectionService;
import com.fieldops.inspection.domain.Inspection;
import com.fieldops.inspection.domain.InspectionItemSnapshot;
import com.fieldops.inspection.domain.InspectionResponse;
import com.fieldops.inspection.repository.InspectionItemSnapshotRepository;
import com.fieldops.inspection.repository.InspectionResponseRepository;
import com.fieldops.review.domain.NonConformity;
import com.fieldops.review.domain.NonConformityStatus;
import com.fieldops.review.dto.CreateNonConformityRequest;
import com.fieldops.review.dto.UpdateNonConformityRequest;
import com.fieldops.review.repository.NonConformityRepository;
import com.fieldops.shared.exception.BusinessRuleException;
import com.fieldops.shared.exception.NotFoundException;
import com.fieldops.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class NonConformityService {

    private final NonConformityRepository nonConformityRepository;
    private final InspectionItemSnapshotRepository snapshotRepository;
    private final InspectionResponseRepository responseRepository;
    private final InspectionService inspectionService;
    private final AuditService auditService;

    public NonConformity create(UUID inspectionId, CreateNonConformityRequest request, User actor) {
        Inspection inspection = inspectionService.getById(inspectionId);
        InspectionItemSnapshot item = null;
        if (request.inspectionItemId() != null) {
            item = snapshotRepository.findById(request.inspectionItemId())
                    .orElseThrow(() -> NotFoundException.of("InspectionItemSnapshot", request.inspectionItemId()));
            if (!item.getInspection().getId().equals(inspectionId)) {
                throw new BusinessRuleException("ITEM_INSPECTION_MISMATCH", "The item does not belong to this inspection");
            }
        }
        InspectionResponse response = null;
        if (request.responseId() != null) {
            response = responseRepository.findById(request.responseId())
                    .orElseThrow(() -> NotFoundException.of("InspectionResponse", request.responseId()));
            if (!response.getInspection().getId().equals(inspectionId)) {
                throw new BusinessRuleException("RESPONSE_INSPECTION_MISMATCH", "The response does not belong to this inspection");
            }
        }
        NonConformity nc = new NonConformity();
        nc.setInspection(inspection);
        nc.setInspectionItem(item);
        nc.setResponse(response);
        nc.setTitle(request.title());
        nc.setDescription(request.description());
        nc.setSeverity(request.severity());
        nc.setStatus(NonConformityStatus.OPEN);
        nc.setCreatedBy(actor);
        nc.setCreatedAtDevice(Instant.now());
        nc.setServerReceivedAt(Instant.now());
        nc = nonConformityRepository.save(nc);
        auditService.record(inspectionId, actor.getId(), "NON_CONFORMITY_CREATED", "NonConformity", nc.getId());
        return nc;
    }

    @Transactional(readOnly = true)
    public List<NonConformity> listByInspection(UUID inspectionId) {
        return nonConformityRepository.findByInspectionId(inspectionId);
    }

    @Transactional(readOnly = true)
    public NonConformity getById(UUID id) {
        return nonConformityRepository.findById(id).orElseThrow(() -> NotFoundException.of("NonConformity", id));
    }

    public NonConformity update(UUID id, UpdateNonConformityRequest request) {
        NonConformity nc = getById(id);
        nc.setTitle(request.title());
        nc.setDescription(request.description());
        nc.setSeverity(request.severity());
        return nonConformityRepository.save(nc);
    }

    public NonConformity updateStatus(UUID id, NonConformityStatus status, User actor) {
        NonConformity nc = getById(id);
        nc.setStatus(status);
        nc = nonConformityRepository.save(nc);
        auditService.record(nc.getInspection().getId(), actor.getId(), "NON_CONFORMITY_STATUS_CHANGED", "NonConformity", nc.getId());
        return nc;
    }
}
