package com.hireflow.auth.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fixed, one-to-one mapping between a persisted HireFlow role and the Spring Security
 * authority it grants. Every {@code @PreAuthorize} expression in this service matches on
 * exactly these values, so the mapping is pinned here.
 */
class UserRoleTest {

    @Test
    void everyRoleMapsToOneRolePrefixedAuthority() {
        assertThat(UserRole.CANDIDATE.authority()).isEqualTo("ROLE_CANDIDATE");
        assertThat(UserRole.RECRUITER.authority()).isEqualTo("ROLE_RECRUITER");
        assertThat(UserRole.ADMIN.authority()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void everyAuthorityCarriesTheRolePrefixHasRoleExpressionsRequire() {
        // hasRole("X") matches only authorities literally prefixed with "ROLE_".
        for (UserRole role : UserRole.values()) {
            assertThat(role.authority()).startsWith("ROLE_");
            assertThat(role.authority()).isEqualTo("ROLE_" + role.name());
        }
    }

    @Test
    void theRoleSetIsExactlyTheThreeHireFlowRoles() {
        assertThat(UserRole.values()).containsExactly(
                UserRole.CANDIDATE, UserRole.RECRUITER, UserRole.ADMIN);
    }
}
