package com.hireflow.auth.service;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.hireflow.auth.entity.User;
import com.hireflow.auth.entity.UserRole;
import com.hireflow.auth.exception.InvalidCredentialsException;
import com.hireflow.auth.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Focused credential-verification tests (Step 13, checkpoint "add password
 * authentication"): correct credentials succeed, and every failure mode — unknown email,
 * legacy account without a hash, wrong password, disabled account — collapses into one
 * generic {@link InvalidCredentialsException} that reveals nothing about the account.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final String RAW_PASSWORD = "Sup3r-Secret!";

    @Mock
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        // A real BCrypt encoder, the same implementation the application wires as a bean.
        passwordEncoder = new BCryptPasswordEncoder();
        authenticationService = new AuthenticationService(userRepository, passwordEncoder);
    }

    @Test
    void verifiesCorrectCredentials() {
        User user = enabledUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        User authenticated = authenticationService.verifyCredentials(user.getEmail(), RAW_PASSWORD);

        assertThat(authenticated).isSameAs(user);
    }

    @Test
    void rejectsUnknownEmailWithGenericMessage() {
        String unknownEmail = "missing-" + UUID.randomUUID() + "@example.com";
        when(userRepository.findByEmail(unknownEmail)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.verifyCredentials(unknownEmail, RAW_PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void rejectsWrongPasswordWithSameGenericMessage() {
        User user = enabledUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authenticationService.verifyCredentials(user.getEmail(), "definitely-wrong"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void rejectsAccountWithoutPasswordHashLikeWrongPassword() {
        // Rows created before Step 13 have no hash; they must fail like any other bad
        // credential instead of authenticating or erroring differently.
        User legacyUser = enabledUser();
        legacyUser.setPasswordHash(null);
        when(userRepository.findByEmail(legacyUser.getEmail())).thenReturn(Optional.of(legacyUser));

        assertThatThrownBy(() -> authenticationService.verifyCredentials(legacyUser.getEmail(), RAW_PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void rejectsDisabledAccount() {
        User user = enabledUser();
        user.setEnabled(false);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authenticationService.verifyCredentials(user.getEmail(), RAW_PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void rejectsBlankPasswordWithoutThrowingUnexpectedErrors() {
        User user = enabledUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authenticationService.verifyCredentials(user.getEmail(), " "))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void failureMessageNeverContainsEmailOrPassword() {
        String email = "secret-" + UUID.randomUUID() + "@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.verifyCredentials(email, RAW_PASSWORD))
                .hasMessageNotContaining(email)
                .hasMessageNotContaining(RAW_PASSWORD);
    }

    private User enabledUser() {
        User user = new User();
        user.setEmail("auth-" + UUID.randomUUID() + "@example.com");
        user.setFirstName("Jane");
        user.setLastName("Doe");
        user.setRole(UserRole.CANDIDATE);
        user.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        return user;
    }
}
