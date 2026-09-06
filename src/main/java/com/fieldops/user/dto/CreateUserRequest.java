package com.fieldops.user.dto;

import com.fieldops.user.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "name is required") String name,
        @NotBlank(message = "email is required") @Email(message = "email must be valid") String email,
        @NotBlank(message = "password is required") @Size(min = 8, message = "password must be at least 8 characters") String password,
        @NotNull(message = "role is required") Role role,
        String phone,
        boolean temporaryPassword
) {
}
