package com.hireflow.auth.dto;

import com.hireflow.auth.entity.UserRole;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Payload used by an administrator to assign a role to an existing user.
 *
 * <p>This is the <b>only</b> payload in HireFlow that carries a role, and the endpoint it
 * serves ({@code PUT /api/users/{id}/role}) is restricted to the {@code ROLE_ADMIN}
 * authority. No create or update DTO accepts a role, so no other code path can set one —
 * not registration, not profile updates, not Google sign-up (which fixes the role
 * server-side at registration). The role change is persisted immediately and appears in
 * the tokens issued from that moment on; tokens already in flight keep the role they were
 * signed with until they expire.</p>
 */
@Schema(description = "Payload used by an administrator to assign a role to a user. "
        + "Served by the ADMIN-only role endpoint; no other endpoint accepts a role.")
public record UpdateUserRoleRequest(
        @NotNull(message = "role must not be null")
        @Schema(description = "The role to assign to the user",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "RECRUITER")
        UserRole role) {
}
