package com.fieldops.user.dto;

import com.fieldops.user.domain.Role;
import com.fieldops.user.domain.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        Role role,
        UserStatus status,
        String phone,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
