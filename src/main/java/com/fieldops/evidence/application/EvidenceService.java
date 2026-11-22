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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class EvidenceService {

    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    public static final int MAX_EVIDENCE_PER_INSPECTION = 10;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".pdf");
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
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessRuleException("FILE_TOO_LARGE", "Evidence files cannot exceed 10 MB");
        }
        ValidatedFile validated = validateFile(file);
        if (evidenceRepository.countByInspectionId(inspection.getId()) >= MAX_EVIDENCE_PER_INSPECTION) {
            throw new BusinessRuleException("EVIDENCE_LIMIT_EXCEEDED", "An inspection cannot have more than 10 evidence files");
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
        registerStorageCompensation(stored.storageKey());

        Evidence evidence = new Evidence();
        evidence.setInspection(inspection);
        evidence.setResponse(response);
        evidence.setNonConformityId(nonConformityId);
        evidence.setType(validated.mimeType().equals("application/pdf") ? EvidenceType.DOCUMENT : EvidenceType.PHOTO);
        evidence.setStorageKey(stored.storageKey());
        evidence.setOriginalFileName(file.getOriginalFilename());
        evidence.setMimeType(validated.mimeType());
        evidence.setSizeBytes(stored.sizeBytes());
        evidence.setChecksum(stored.checksum());
        evidence.setDescription(description);
        evidence.setLatitude(latitude);
        evidence.setLongitude(longitude);
        evidence.setCapturedAtDevice(Instant.now());
        evidence.setServerReceivedAt(Instant.now());
        evidence.setUploadedAt(Instant.now());
        evidence.setCreatedBy(actor);
        try {
            evidence = evidenceRepository.save(evidence);
            auditService.record(inspection.getId(), actor.getId(), "EVIDENCE_ADDED", "Evidence", evidence.getId());
            return evidence;
        } catch (RuntimeException failure) {
            try {
                evidenceStorage.delete(stored.storageKey());
            } catch (RuntimeException cleanupFailure) {
                // Preserve the original failure; storage implementations should retry/reconcile separately.
            }
            throw failure;
        }
    }

    @Transactional(readOnly = true)
    public List<Evidence> listByInspection(UUID inspectionId) {
        return evidenceRepository.findByInspectionId(inspectionId);
    }

    @Transactional(readOnly = true)
    public Evidence getById(UUID id) {
        return evidenceRepository.findWithInspectionById(id).orElseThrow(() -> NotFoundException.of("Evidence", id));
    }

    @Transactional(readOnly = true)
    public EvidenceStorage.StoredObject readContent(Evidence evidence) {
        return evidenceStorage.read(evidence.getStorageKey());
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

    private ValidatedFile validateFile(MultipartFile file) {
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessRuleException("UNSUPPORTED_FILE_TYPE", "Only JPEG, PNG and PDF evidence files are supported");
        }
        try {
            byte[] bytes = file.getBytes();
            String detected = detectMime(bytes);
            String expected = extension.equals(".pdf") ? "application/pdf"
                    : extension.equals(".png") ? "image/png" : "image/jpeg";
            if (!expected.equals(detected)) {
                throw new BusinessRuleException("INVALID_FILE_CONTENT", "File extension and binary content do not match");
            }
            String declared = normalizeMime(file.getContentType());
            if (declared != null && !declared.equals("application/octet-stream") && !declared.equals(detected)) {
                throw new BusinessRuleException("INVALID_FILE_CONTENT", "Declared MIME type does not match file content");
            }
            return new ValidatedFile(detected);
        } catch (java.io.IOException e) {
            throw new BusinessRuleException("INVALID_FILE", "Could not read the uploaded file");
        }
    }

    private void registerStorageCompensation(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        evidenceStorage.delete(storageKey);
                    } catch (RuntimeException ignored) {
                        // Keep the transaction failure as the primary error; cleanup is idempotent/best-effort.
                    }
                }
            }
        });
    }

    private String detectMime(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (bytes.length >= png.length && java.util.Arrays.equals(java.util.Arrays.copyOf(bytes, png.length), png)) {
            return "image/png";
        }
        if (bytes.length >= 5 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            return "application/pdf";
        }
        throw new BusinessRuleException("INVALID_FILE_CONTENT", "File content is not a supported JPEG, PNG or PDF");
    }

    private String extensionOf(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase(Locale.ROOT) : "";
    }

    private String normalizeMime(String mime) {
        if (mime == null) return null;
        return "image/jpg".equalsIgnoreCase(mime) ? "image/jpeg" : mime.toLowerCase(Locale.ROOT);
    }

    private record ValidatedFile(String mimeType) {}
}
