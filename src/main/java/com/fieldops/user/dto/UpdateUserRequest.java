package com.fieldops.user.dto;

import com.fieldops.user.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRequest(
        @NotBlank(message = "name is required") String name,
        @NotNull(message = "role is required") Role role,
        String phone
) {
}
