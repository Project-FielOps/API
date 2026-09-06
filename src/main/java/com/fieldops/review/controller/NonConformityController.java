package com.fieldops.review.controller;

import com.fieldops.auth.security.FieldOpsUserDetails;
import com.fieldops.review.application.NonConformityService;
import com.fieldops.review.domain.NonConformity;
import com.fieldops.review.dto.CreateNonConformityRequest;
import com.fieldops.review.dto.NonConformityResponse;
import com.fieldops.review.dto.UpdateNonConformityRequest;
import com.fieldops.review.dto.UpdateNonConformityStatusRequest;
import com.fieldops.review.mapper.ReviewMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inspections/{inspectionId}/non-conformities")
@RequiredArgsConstructor
public class NonConformityController {

    private final NonConformityService nonConformityService;
    private final ReviewMapper reviewMapper;

    @PostMapping
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<NonConformityResponse> create(@PathVariable UUID inspectionId,
                                                         @Valid @RequestBody CreateNonConformityRequest request,
                                                         @AuthenticationPrincipal FieldOpsUserDetails principal) {
        NonConformity created = nonConformityService.create(inspectionId, request, principal.getUser());
        return ResponseEntity.created(URI.create("/api/v1/inspections/" + inspectionId + "/non-conformities/" + created.getId()))
                .body(reviewMapper.toResponse(created));
    }

    @GetMapping
    public List<NonConformityResponse> list(@PathVariable UUID inspectionId,
                                            @AuthenticationPrincipal FieldOpsUserDetails principal) {
        return nonConformityService.listByInspection(inspectionId, principal.getUser()).stream()
                .map(reviewMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public NonConformityResponse get(@PathVariable UUID inspectionId, @PathVariable UUID id,
                                     @AuthenticationPrincipal FieldOpsUserDetails principal) {
        NonConformity nc = nonConformityService.getById(id);
        if (!nc.getInspection().getId().equals(inspectionId)) {
            throw com.fieldops.shared.exception.NotFoundException.of("NonConformity", id);
        }
        nonConformityService.requireReadAccess(nc, principal.getUser());
        return reviewMapper.toResponse(nc);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TECHNICIAN','SUPERVISOR')")
    public NonConformityResponse update(@PathVariable UUID inspectionId, @PathVariable UUID id,
                                         @Valid @RequestBody UpdateNonConformityRequest request,
                                         @AuthenticationPrincipal FieldOpsUserDetails principal) {
        ensurePathScope(inspectionId, id, principal);
        return reviewMapper.toResponse(nonConformityService.update(id, request, principal.getUser()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public NonConformityResponse updateStatus(@PathVariable UUID inspectionId, @PathVariable UUID id,
                                               @Valid @RequestBody UpdateNonConformityStatusRequest request,
                                               @AuthenticationPrincipal FieldOpsUserDetails principal) {
        ensurePathScope(inspectionId, id, principal);
        return reviewMapper.toResponse(nonConformityService.updateStatus(id, request.status(), principal.getUser()));
    }

    private void ensurePathScope(UUID inspectionId, UUID id, FieldOpsUserDetails principal) {
        NonConformity nc = nonConformityService.getById(id);
        if (!nc.getInspection().getId().equals(inspectionId)) {
            throw com.fieldops.shared.exception.NotFoundException.of("NonConformity", id);
        }
        nonConformityService.requireReadAccess(nc, principal.getUser());
    }
}
