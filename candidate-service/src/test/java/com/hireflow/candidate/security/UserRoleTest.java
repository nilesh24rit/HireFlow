package com.hireflow.candidate.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fixed, one-to-one mapping between the role claim of a verified token and the Spring
 * Security authority candidate-service grants. {@code @PreAuthorize} expressions match on
 * exactly these values, so the mapping — and the refusal of anything unknown — is pinned
 * here.
 */
class UserRoleTest {

    @Test
    void everyRoleMapsToOneRolePrefixedAuthority() {
        assertThat(UserRole.CANDIDATE.authority()).isEqualTo("ROLE_CANDIDATE");
        assertThat(UserRole.RECRUITER.authority()).isEqualTo("ROLE_RECRUITER");
        assertThat(UserRole.ADMIN.authority()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void aKnownClaimResolvesToItsRole() {
        assertThat(UserRole.fromClaim("CANDIDATE")).contains(UserRole.CANDIDATE);
        assertThat(UserRole.fromClaim("RECRUITER")).contains(UserRole.RECRUITER);
        assertThat(UserRole.fromClaim("ADMIN")).contains(UserRole.ADMIN);
    }

    @Test
    void missingOrUnknownClaimsResolveToNothing() {
        // Never a default role: a token in this state is rejected by JwtService rather
        // than authenticated with a guessed authority.
        assertThat(UserRole.fromClaim(null)).isEmpty();
        assertThat(UserRole.fromClaim("")).isEmpty();
        assertThat(UserRole.fromClaim("   ")).isEmpty();
        assertThat(UserRole.fromClaim("SUPERUSER")).isEmpty();
        assertThat(UserRole.fromClaim("candidate")).isEmpty();
        assertThat(UserRole.fromClaim("ROLE_CANDIDATE")).isEmpty();
    }
}
