package com.fieldops.user.dto;

import com.fieldops.user.domain.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "status is required") UserStatus status
) {
}
