package com.hireflow.auth.dto;

import java.time.Instant;
import java.util.UUID;

import com.hireflow.auth.entity.UserRole;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User profile returned by the auth service")
public record UserResponse(
        @Schema(description = "User identifier", example = "3f1a8f9e-0d2c-4a6b-9f3e-1b2c3d4e5f60")
        UUID id,
        @Schema(description = "Registered email address", example = "jane.doe@example.com")
        String email,
        @Schema(description = "First name", example = "Jane")
        String firstName,
        @Schema(description = "Last name", example = "Doe")
        String lastName,
        @Schema(description = "Role of the user", example = "CANDIDATE")
        UserRole role,
        @Schema(description = "Whether the user account is enabled", example = "true")
        boolean enabled,
        @Schema(description = "Creation timestamp in UTC")
        Instant createdAt,
        @Schema(description = "Last update timestamp in UTC")
        Instant updatedAt) {
}
