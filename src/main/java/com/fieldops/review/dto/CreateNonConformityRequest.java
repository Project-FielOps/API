package com.fieldops.review.dto;

import com.fieldops.review.domain.NonConformitySeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateNonConformityRequest(
        UUID inspectionItemId,
        UUID responseId,
        @NotBlank(message = "title is required") String title,
        String description,
        @NotNull(message = "severity is required") NonConformitySeverity severity
) {
}
