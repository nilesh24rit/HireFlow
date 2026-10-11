package com.hireflow.auth;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.hireflow.auth.dto.GoogleIdentity;
import com.hireflow.auth.entity.OAuth2Provider;
import com.hireflow.auth.entity.User;
import com.hireflow.auth.entity.UserOAuthIdentity;
import com.hireflow.auth.entity.UserRole;
import com.hireflow.auth.exception.OAuth2IdentityException;
import com.hireflow.auth.exception.OAuth2IdentityException.Reason;
import com.hireflow.auth.repository.UserOAuthIdentityRepository;
import com.hireflow.auth.repository.UserRepository;
import com.hireflow.auth.security.TestSigningKeys;
import com.hireflow.auth.service.GoogleIdentityService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Verifies Google identity mapping against a real database, focusing on the guarantees that
 * unit tests with mocks cannot prove:
 *
 * <ul>
 *   <li>a new Google sign-in really persists both the user and the identity link;</li>
 *   <li>account creation and identity linking share one transaction — if linking fails, the
 *       user is rolled back too, so no half-created account survives a failed sign-in;</li>
 *   <li>the (provider, subject) uniqueness constraint is the database-level backstop against
 *       linking one Google identity twice;</li>
 *   <li>an unlinked identity whose email matches an existing account is refused against the
 *       real user table, not silently linked.</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class GoogleIdentityServiceIntegrationTest {

    private static final String DATABASE_NAME = "hireflow_auth_google_identity";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName(DATABASE_NAME);

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("hireflow.jwt.signing-key", () -> TestSigningKeys.VALID);
    }

    @Autowired
    private GoogleIdentityService service;

    @Autowired
    private UserRepository userRepository;

    // Spied so the test can force the identity write to fail and observe the rollback.
    @MockitoSpyBean
    private UserOAuthIdentityRepository identityRepository;

    private GoogleIdentity identity(String subject, String email) {
        return new GoogleIdentity(subject, email, "Given", "Family");
    }

    @Test
    void newGoogleSignInPersistsUserAndIdentity() {
        String subject = "persist-" + UUID.randomUUID();
        String email = subject + "@example.com";

        User created = service.authenticateWithGoogle(identity(subject, email));

        assertThat(created.getId()).isNotNull();
        assertThat(userRepository.findById(created.getId())).isPresent();
        assertThat(identityRepository.findByProviderAndProviderSubject(OAuth2Provider.GOOGLE, subject))
                .isPresent()
                .get()
                .extracting(UserOAuthIdentity::getUserId)
                .isEqualTo(created.getId());
    }

    @Test
    void returningGoogleSignInResolvesToTheSameUser() {
        String subject = "returning-" + UUID.randomUUID();
        String email = subject + "@example.com";

        User first = service.authenticateWithGoogle(identity(subject, email));
        User second = service.authenticateWithGoogle(identity(subject, email));

        assertThat(second.getId()).isEqualTo(first.getId());
        // No second account was created for the same Google identity.
        assertThat(userRepository.findByEmail(email)).isPresent();
    }

    @Test
    void failedIdentityLinkRollsBackTheNewUser() {
        String subject = "rollback-" + UUID.randomUUID();
        String email = subject + "@example.com";
        doThrow(new DataIntegrityViolationException("forced"))
                .when(identityRepository).saveAndFlush(any(UserOAuthIdentity.class));

        // The service surfaces the failure as a clean conflict rather than a raw
        // database exception.
        assertThatThrownBy(() -> service.authenticateWithGoogle(identity(subject, email)))
                .isInstanceOf(OAuth2IdentityException.class);

        // The user insert shared the transaction, so it must be gone too: a failed
        // sign-in never leaves a half-created account behind.
        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void databaseRefusesTheSameProviderSubjectTwice() {
        String subject = "unique-" + UUID.randomUUID();
        User first = service.authenticateWithGoogle(identity(subject, subject + "-first@example.com"));

        // Bypass the service and try to link the same Google subject to another user
        // directly, exactly as a lost race would.
        User other = new User();
        other.setEmail(subject + "-second@example.com");
        other.setFirstName("Other");
        other.setLastName("User");
        other.setRole(UserRole.CANDIDATE);
        userRepository.saveAndFlush(other);

        assertThatThrownBy(() -> identityRepository.saveAndFlush(
                new UserOAuthIdentity(other.getId(), OAuth2Provider.GOOGLE, subject)))
                .isInstanceOf(DataIntegrityViolationException.class);

        // The first linkage is intact.
        assertThat(identityRepository.findByProviderAndProviderSubject(OAuth2Provider.GOOGLE, subject))
                .isPresent()
                .get()
                .extracting(UserOAuthIdentity::getUserId)
                .isEqualTo(first.getId());
    }

    @Test
    void refusesToAutoLinkWhenEmailMatchesExistingAccount() {
        String existingEmail = "existing-" + UUID.randomUUID() + "@example.com";
        User existing = new User();
        existing.setEmail(existingEmail);
        existing.setFirstName("Existing");
        existing.setLastName("User");
        existing.setRole(UserRole.CANDIDATE);
        userRepository.saveAndFlush(existing);

        String unlinkedSubject = "conflict-" + UUID.randomUUID();
        assertThatThrownBy(() -> service.authenticateWithGoogle(identity(unlinkedSubject, existingEmail)))
                .isInstanceOf(OAuth2IdentityException.class)
                .extracting(ex -> ((OAuth2IdentityException) ex).reason())
                .isEqualTo(Reason.EMAIL_ALREADY_REGISTERED);

        // The Google identity was not linked and the existing account is untouched.
        assertThat(identityRepository.existsByProviderAndProviderSubject(OAuth2Provider.GOOGLE, unlinkedSubject))
                .isFalse();
        assertThat(userRepository.findByEmail(existingEmail)).isPresent();
        assertThat(identityRepository.findAll().stream()
                .noneMatch(identity -> identity.getUserId().equals(
                        userRepository.findByEmail(existingEmail).orElseThrow().getId())))
                .isTrue();
    }

    @Test
    void authenticatedLinkingGivesOneUserBothSignInMethods() {
        String passwordEmail = "both-" + UUID.randomUUID() + "@example.com";
        User account = new User();
        account.setEmail(passwordEmail);
        account.setFirstName("Both");
        account.setLastName("Methods");
        account.setRole(UserRole.CANDIDATE);
        userRepository.saveAndFlush(account);

        String subject = "both-sub-" + UUID.randomUUID();
        service.linkIdentity(account, identity(subject, passwordEmail));

        // The same account now signs in with Google too.
        User viaGoogle = service.authenticateWithGoogle(identity(subject, passwordEmail));
        assertThat(viaGoogle.getId()).isEqualTo(account.getId());
    }
}
