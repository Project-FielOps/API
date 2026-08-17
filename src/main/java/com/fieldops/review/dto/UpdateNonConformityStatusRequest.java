package com.fieldops.review.dto;

import com.fieldops.review.domain.NonConformityStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateNonConformityStatusRequest(
        @NotNull(message = "status is required") NonConformityStatus status
) {
}
