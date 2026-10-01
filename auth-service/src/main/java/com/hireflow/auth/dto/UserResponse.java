package com.hireflow.auth.dto;

import java.time.Instant;
import java.util.UUID;

import com.hireflow.auth.entity.UserRole;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        UserRole role,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}
