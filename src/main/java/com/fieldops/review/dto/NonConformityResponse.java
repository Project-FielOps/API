package com.fieldops.review.dto;

import com.fieldops.review.domain.NonConformitySeverity;
import com.fieldops.review.domain.NonConformityStatus;

import java.time.Instant;
import java.util.UUID;

public record NonConformityResponse(
        UUID id,
        UUID inspectionId,
        UUID inspectionItemId,
        UUID responseId,
        String title,
        String description,
        NonConformitySeverity severity,
        NonConformityStatus status,
        UUID createdBy,
        Instant createdAt,
        Long version
) {
}
