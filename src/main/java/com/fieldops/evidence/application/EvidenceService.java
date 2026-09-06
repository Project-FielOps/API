package com.fieldops.evidence.application;

import com.fieldops.audit.application.AuditService;
import com.fieldops.evidence.domain.Evidence;
import com.fieldops.evidence.domain.EvidenceType;
import com.fieldops.evidence.repository.EvidenceRepository;
import com.fieldops.review.domain.NonConformity;
import com.fieldops.review.repository.NonConformityRepository;
import com.fieldops.inspection.domain.Inspection;
import com.fieldops.inspection.domain.InspectionResponse;
import com.fieldops.inspection.domain.InspectionStatus;
import com.fieldops.inspection.repository.InspectionResponseRepository;
import com.fieldops.shared.exception.BusinessRuleException;
import com.fieldops.shared.exception.ForbiddenOperationException;
import com.fieldops.shared.exception.NotFoundException;
import com.fieldops.user.domain.Role;
import com.fieldops.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class EvidenceService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/heic");
    private static final Set<InspectionStatus> EDITABLE_STATUSES = Set.of(InspectionStatus.IN_PROGRESS);

    private final EvidenceRepository evidenceRepository;
    private final InspectionResponseRepository responseRepository;
    private final EvidenceStorage evidenceStorage;
    private final AuditService auditService;
    private final NonConformityRepository nonConformityRepository;

    public Evidence upload(Inspection inspection, UUID responseId, UUID nonConformityId, String description,
                            BigDecimal latitude, BigDecimal longitude, MultipartFile file, User actor) {
        if (!EDITABLE_STATUSES.contains(inspection.getStatus())) {
            throw new BusinessRuleException("INSPECTION_LOCKED",
                    "Evidence can only be added while the inspection is IN_PROGRESS");
        }
        if (actor.getRole() != Role.TECHNICIAN || !inspection.getTechnician().getId().equals(actor.getId())) {
            throw new ForbiddenOperationException("Only the assigned technician can attach evidence");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("EMPTY_FILE", "No file was provided");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessRuleException("UNSUPPORTED_FILE_TYPE", "Only photo evidence (jpeg/png/webp/heic) is supported in the MVP");
        }
        InspectionResponse response = null;
        if (responseId != null) {
            response = responseRepository.findById(responseId).orElseThrow(() -> NotFoundException.of("InspectionResponse", responseId));
            if (!response.getInspection().getId().equals(inspection.getId())) {
                throw new BusinessRuleException("RESPONSE_INSPECTION_MISMATCH", "The response does not belong to this inspection");
            }
        }
        if (nonConformityId != null) {
            NonConformity nc = nonConformityRepository.findById(nonConformityId)
                    .orElseThrow(() -> NotFoundException.of("NonConformity", nonConformityId));
            if (!nc.getInspection().getId().equals(inspection.getId())) {
                throw new BusinessRuleException("NON_CONFORMITY_INSPECTION_MISMATCH",
                        "The non-conformity does not belong to this inspection");
            }
        }

        EvidenceStorage.StoredFile stored = evidenceStorage.store(inspection.getId(), file);

        Evidence evidence = new Evidence();
        evidence.setInspection(inspection);
        evidence.setResponse(response);
        evidence.setNonConformityId(nonConformityId);
        evidence.setType(EvidenceType.PHOTO);
        evidence.setStorageKey(stored.storageKey());
        evidence.setOriginalFileName(file.getOriginalFilename());
        evidence.setMimeType(contentType);
        evidence.setSizeBytes(stored.sizeBytes());
        evidence.setChecksum(stored.checksum());
        evidence.setDescription(description);
        evidence.setLatitude(latitude);
        evidence.setLongitude(longitude);
        evidence.setCapturedAtDevice(Instant.now());
        evidence.setServerReceivedAt(Instant.now());
        evidence.setUploadedAt(Instant.now());
        evidence.setCreatedBy(actor);
        evidence = evidenceRepository.save(evidence);

        auditService.record(inspection.getId(), actor.getId(), "EVIDENCE_ADDED", "Evidence", evidence.getId());
        return evidence;
    }

    @Transactional(readOnly = true)
    public List<Evidence> listByInspection(UUID inspectionId) {
        return evidenceRepository.findByInspectionId(inspectionId);
    }

    @Transactional(readOnly = true)
    public Evidence getById(UUID id) {
        return evidenceRepository.findById(id).orElseThrow(() -> NotFoundException.of("Evidence", id));
    }

    public void delete(UUID id, User actor) {
        Evidence evidence = getById(id);
        Inspection inspection = evidence.getInspection();
        if (inspection.getStatus() == InspectionStatus.APPROVED) {
            throw new BusinessRuleException("INSPECTION_LOCKED", "Evidence on an approved inspection cannot be removed");
        }
        if (actor.getRole() != Role.TECHNICIAN || !inspection.getTechnician().getId().equals(actor.getId())) {
            throw new ForbiddenOperationException("Only the assigned technician can remove their own evidence");
        }
        evidenceStorage.delete(evidence.getStorageKey());
        evidenceRepository.delete(evidence);
        auditService.record(inspection.getId(), actor.getId(), "EVIDENCE_REMOVED", "Evidence", id);
    }
}
