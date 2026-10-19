package com.fieldops.review.controller;

import com.fieldops.auth.security.FieldOpsUserDetails;
import com.fieldops.review.application.NonConformityService;
import com.fieldops.review.domain.NonConformitySeverity;
import com.fieldops.review.domain.NonConformityStatus;
import com.fieldops.review.dto.NonConformityResponse;
import com.fieldops.review.mapper.ReviewMapper;
import com.fieldops.shared.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/non-conformities")
@RequiredArgsConstructor
public class NonConformityListController {

    private final NonConformityService nonConformityService;
    private final ReviewMapper reviewMapper;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','TECHNICIAN')")
    public PageResponse<NonConformityResponse> list(
            @RequestParam(required = false) UUID inspectionId,
            @RequestParam(required = false) NonConformitySeverity severity,
            @RequestParam(required = false) NonConformityStatus status,
            Pageable pageable,
            @AuthenticationPrincipal FieldOpsUserDetails principal) {
        return PageResponse.of(nonConformityService.listForActor(
                principal.getUser(), inspectionId, severity, status, pageable).map(reviewMapper::toResponse));
    }
}
