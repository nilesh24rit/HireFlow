package com.hireflow.candidate.security;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The identity a verified HireFlow access token authenticates.
 *
 * <p>Both fields come from a token whose signature, algorithm, expiry and issuer were already
 * checked by {@link JwtService}; the role in particular is the server-derived claim auth-service
 * wrote from the persisted user record, so an authority here always traces back to trusted
 * data. Authorization decisions in candidate-service are made against this identity and the
 * persisted ownership of the resource — never against anything the request asserted.</p>
 *
 * @param userId the stable user id carried in the subject claim
 * @param role   the role carried in the {@code role} claim
 */
public record AuthenticatedUser(UUID userId, UserRole role) {

    public AuthenticatedUser {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(role, "role must not be null");
    }

    /** The single Spring Security authority this identity carries. */
    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    /** True when this identity holds the platform administrator role. */
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
