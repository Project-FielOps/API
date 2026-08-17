package com.fieldops.review.dto;

import com.fieldops.review.domain.ReviewDecision;

import java.time.Instant;
import java.util.UUID;

public record InspectionReviewResponse(
        UUID id,
        UUID inspectionId,
        UUID reviewerId,
        String reviewerName,
        ReviewDecision decision,
        String reason,
        String comments,
        Instant reviewedAt,
        Integer reviewCycle
) {
}
