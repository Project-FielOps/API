package com.fieldops.client.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ClientUpsertRequest(
        @NotBlank(message = "name is required") String name,
        String legalName,
        String document,
        @Email(message = "email must be valid") String email,
        String phone
) {
}
