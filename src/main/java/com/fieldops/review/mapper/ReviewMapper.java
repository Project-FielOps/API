package com.fieldops.review.mapper;

import com.fieldops.review.domain.InspectionReview;
import com.fieldops.review.domain.NonConformity;
import com.fieldops.review.dto.InspectionReviewResponse;
import com.fieldops.review.dto.NonConformityResponse;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ReviewMapper {

    public NonConformityResponse toResponse(NonConformity nc) {
        return new NonConformityResponse(nc.getId(), nc.getInspection().getId(),
                Optional.ofNullable(nc.getInspectionItem()).map(i -> i.getId()).orElse(null),
                Optional.ofNullable(nc.getResponse()).map(r -> r.getId()).orElse(null),
                nc.getTitle(), nc.getDescription(), nc.getSeverity(), nc.getStatus(),
                nc.getCreatedBy().getId(), nc.getCreatedAt(), nc.getVersion());
    }

    public InspectionReviewResponse toResponse(InspectionReview r) {
        return new InspectionReviewResponse(r.getId(), r.getInspection().getId(), r.getReviewer().getId(),
                r.getReviewer().getName(), r.getDecision(), r.getReason(), r.getComments(), r.getReviewedAt(), r.getReviewCycle());
    }
}
