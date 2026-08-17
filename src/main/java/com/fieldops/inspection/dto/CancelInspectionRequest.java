package com.fieldops.inspection.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelInspectionRequest(
        @NotBlank(message = "reason is required") String reason
) {
}
