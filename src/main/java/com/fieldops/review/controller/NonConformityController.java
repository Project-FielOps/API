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
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN','SUPERVISOR')")
    public ResponseEntity<NonConformityResponse> create(@PathVariable UUID inspectionId,
                                                         @Valid @RequestBody CreateNonConformityRequest request,
                                                         @AuthenticationPrincipal FieldOpsUserDetails principal) {
        NonConformity created = nonConformityService.create(inspectionId, request, principal.getUser());
        return ResponseEntity.created(URI.create("/api/v1/inspections/" + inspectionId + "/non-conformities/" + created.getId()))
                .body(reviewMapper.toResponse(created));
    }

    @GetMapping
    public List<NonConformityResponse> list(@PathVariable UUID inspectionId) {
        return nonConformityService.listByInspection(inspectionId).stream().map(reviewMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public NonConformityResponse get(@PathVariable UUID inspectionId, @PathVariable UUID id) {
        return reviewMapper.toResponse(nonConformityService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN','SUPERVISOR')")
    public NonConformityResponse update(@PathVariable UUID inspectionId, @PathVariable UUID id,
                                         @Valid @RequestBody UpdateNonConformityRequest request) {
        return reviewMapper.toResponse(nonConformityService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public NonConformityResponse updateStatus(@PathVariable UUID inspectionId, @PathVariable UUID id,
                                               @Valid @RequestBody UpdateNonConformityStatusRequest request,
                                               @AuthenticationPrincipal FieldOpsUserDetails principal) {
        return reviewMapper.toResponse(nonConformityService.updateStatus(id, request.status(), principal.getUser()));
    }
}
