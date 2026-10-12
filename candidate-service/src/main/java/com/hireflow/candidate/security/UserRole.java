package com.hireflow.candidate.security;

import java.util.Optional;

/**
 * The HireFlow roles a bearer token may carry, and the Spring Security authority each one
 * maps to.
 *
 * <p>The role is never read from a request body, a header or any other client-controlled
 * input: it comes from the {@code role} claim of a token that was already verified against
 * the shared signing key, and that claim was written by auth-service from the persisted user
 * record at issuance time. The mapping is deliberately fixed and one-to-one:</p>
 *
 * <pre>
 * CANDIDATE -&gt; ROLE_CANDIDATE
 * RECRUITER -&gt; ROLE_RECRUITER
 * ADMIN     -&gt; ROLE_ADMIN
 * </pre>
 */
public enum UserRole {

    /** A job seeker who owns a candidate profile and applications. */
    CANDIDATE,

    /** A hiring manager who owns jobs and reviews applications. */
    RECRUITER,

    /** An administrator of the platform. */
    ADMIN;

    /** The single Spring Security authority of this role, for example {@code ROLE_ADMIN}. */
    public String authority() {
        return "ROLE_" + name();
    }

    /**
     * Resolves the role carried by a verified token.
     *
     * @param claim the raw {@code role} claim value
     * @return the role, or empty when the claim is missing or names a role HireFlow does not
     *         have — a token in that state is rejected rather than authorized with defaults
     */
    public static Optional<UserRole> fromClaim(String claim) {
        if (claim == null || claim.isBlank()) {
            return Optional.empty();
        }
        for (UserRole role : values()) {
            if (role.name().equals(claim)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
