package com.fieldops.inspection.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectInspectionRequest(
        @NotBlank(message = "reason is required") String reason,
        String comments
) {
}
