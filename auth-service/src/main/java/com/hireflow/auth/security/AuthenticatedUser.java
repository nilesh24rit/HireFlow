package com.hireflow.auth.security;

import java.util.Objects;
import java.util.UUID;

import com.hireflow.auth.entity.UserRole;

/**
 * The trusted identity a validated HireFlow access token stands for.
 *
 * <p>Both fields come from the verified token, and the token's {@code role} claim is itself
 * written by auth-service from the persisted user record at issuance time — never from a
 * client assertion. Authorization therefore always reasons about a role that originated in
 * the database, whether the user signed in with a password or with Google.</p>
 *
 * @param userId the stable user id carried in the subject claim
 * @param role   the role carried in the {@code role} claim, already validated against
 *               {@link UserRole}
 */
public record AuthenticatedUser(UUID userId, UserRole role) {

    public AuthenticatedUser {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(role, "role must not be null");
    }

    /** True when this user holds the platform administrator role. */
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
