package com.fieldops.review.controller;

import com.fieldops.auth.security.FieldOpsUserDetails;
import com.fieldops.inspection.domain.Inspection;
import com.fieldops.inspection.dto.InspectionDetailResponse;
import com.fieldops.inspection.dto.RejectInspectionRequest;
import com.fieldops.inspection.dto.ReviewDecisionRequest;
import com.fieldops.inspection.application.InspectionService;
import com.fieldops.inspection.mapper.InspectionMapper;
import com.fieldops.review.application.ReviewService;
import com.fieldops.review.dto.InspectionReviewResponse;
import com.fieldops.review.mapper.ReviewMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inspections/{inspectionId}")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewMapper reviewMapper;
    private final InspectionService inspectionService;
    private final InspectionMapper inspectionMapper;

    @PostMapping("/begin-review")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public InspectionDetailResponse beginReview(@PathVariable UUID inspectionId, @AuthenticationPrincipal FieldOpsUserDetails principal) {
        return toDetail(reviewService.beginReview(inspectionId, principal.getUser()));
    }

    @PostMapping("/approve")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public InspectionDetailResponse approve(@PathVariable UUID inspectionId, @RequestBody(required = false) ReviewDecisionRequest request,
                                             @AuthenticationPrincipal FieldOpsUserDetails principal) {
        String comments = request == null ? null : request.comments();
        return toDetail(reviewService.approve(inspectionId, comments, principal.getUser()));
    }

    @PostMapping("/reject")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public InspectionDetailResponse reject(@PathVariable UUID inspectionId, @Valid @RequestBody RejectInspectionRequest request,
                                            @AuthenticationPrincipal FieldOpsUserDetails principal) {
        return toDetail(reviewService.reject(inspectionId, request.reason(), request.comments(), principal.getUser()));
    }

    @GetMapping("/reviews")
    public List<InspectionReviewResponse> listReviews(@PathVariable UUID inspectionId) {
        return reviewService.listByInspection(inspectionId).stream().map(reviewMapper::toResponse).toList();
    }

    private InspectionDetailResponse toDetail(Inspection inspection) {
        return inspectionMapper.toDetail(inspection, inspectionService.getSnapshots(inspection.getId()),
                inspectionService.getAnswersByItemId(inspection.getId()));
    }
}
