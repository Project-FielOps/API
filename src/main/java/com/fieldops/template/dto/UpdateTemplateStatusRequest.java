package com.fieldops.template.dto;

import com.fieldops.template.domain.TemplateStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTemplateStatusRequest(
        @NotNull(message = "status is required") TemplateStatus status
) {
}
