package com.hireflow.candidate.security;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The identity a verified token authenticates: the single authority it carries and the
 * administrator flag service code may branch on. Both derive exclusively from the token's
 * verified claims, never from request input.
 */
class AuthenticatedUserTest {

    @Test
    void carriesExactlyTheAuthorityOfItsRole() {
        UUID userId = UUID.randomUUID();

        assertThat(new AuthenticatedUser(userId, UserRole.CANDIDATE).authorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_CANDIDATE");
        assertThat(new AuthenticatedUser(userId, UserRole.RECRUITER).authorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_RECRUITER");
        assertThat(new AuthenticatedUser(userId, UserRole.ADMIN).authorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void isAdminIsTrueOnlyForTheAdministratorRole() {
        UUID userId = UUID.randomUUID();

        assertThat(new AuthenticatedUser(userId, UserRole.ADMIN).isAdmin()).isTrue();
        assertThat(new AuthenticatedUser(userId, UserRole.CANDIDATE).isAdmin()).isFalse();
        assertThat(new AuthenticatedUser(userId, UserRole.RECRUITER).isAdmin()).isFalse();
    }
}
