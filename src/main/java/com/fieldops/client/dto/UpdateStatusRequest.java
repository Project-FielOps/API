package com.fieldops.client.dto;

import com.fieldops.client.domain.ClientStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull(message = "status is required") ClientStatus status
) {
}
