package com.fieldops.evidence.controller;

import com.fieldops.auth.security.FieldOpsUserDetails;
import com.fieldops.evidence.application.EvidenceService;
import com.fieldops.evidence.domain.Evidence;
import com.fieldops.evidence.dto.EvidenceResponse;
import com.fieldops.evidence.mapper.EvidenceMapper;
import com.fieldops.inspection.application.InspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inspections/{inspectionId}/evidence")
@RequiredArgsConstructor
public class EvidenceController {

    private final EvidenceService evidenceService;
    private final EvidenceMapper evidenceMapper;
    private final InspectionService inspectionService;

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    public ResponseEntity<EvidenceResponse> upload(@PathVariable UUID inspectionId,
                                                    @RequestParam(required = false) UUID responseId,
                                                    @RequestParam(required = false) UUID nonConformityId,
                                                    @RequestParam(required = false) String description,
                                                    @RequestParam(required = false) BigDecimal latitude,
                                                    @RequestParam(required = false) BigDecimal longitude,
                                                    @RequestParam("file") MultipartFile file,
                                                    @AuthenticationPrincipal FieldOpsUserDetails principal) {
        Evidence evidence = evidenceService.upload(inspectionService.getById(inspectionId), responseId, nonConformityId,
                description, latitude, longitude, file, principal.getUser());
        return ResponseEntity.created(URI.create("/api/v1/inspections/" + inspectionId + "/evidence/" + evidence.getId()))
                .body(evidenceMapper.toResponse(evidence));
    }

    @GetMapping
    public List<EvidenceResponse> list(@PathVariable UUID inspectionId) {
        return evidenceService.listByInspection(inspectionId).stream().map(evidenceMapper::toResponse).toList();
    }

    @GetMapping("/{evidenceId}")
    public EvidenceResponse get(@PathVariable UUID inspectionId, @PathVariable UUID evidenceId) {
        return evidenceMapper.toResponse(evidenceService.getById(evidenceId));
    }

    @DeleteMapping("/{evidenceId}")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    public ResponseEntity<Void> delete(@PathVariable UUID inspectionId, @PathVariable UUID evidenceId,
                                        @AuthenticationPrincipal FieldOpsUserDetails principal) {
        evidenceService.delete(evidenceId, principal.getUser());
        return ResponseEntity.noContent().build();
    }
}
