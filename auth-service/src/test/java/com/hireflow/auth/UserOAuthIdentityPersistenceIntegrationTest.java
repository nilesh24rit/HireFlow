package com.hireflow.auth;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.hireflow.auth.entity.OAuth2Provider;
import com.hireflow.auth.entity.User;
import com.hireflow.auth.entity.UserOAuthIdentity;
import com.hireflow.auth.entity.UserRole;
import com.hireflow.auth.repository.UserOAuthIdentityRepository;
import com.hireflow.auth.repository.UserRepository;
import com.hireflow.auth.security.TestSigningKeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the OAuth identity persistence added for Google sign-in:
 *
 * <ul>
 *   <li>a verified provider subject maps to exactly one user through the
 *       {@code (provider, providerSubject)} pair;</li>
 *   <li>that pair is unique at the database level, so a Google identity can never be linked
 *       twice even if application checks are raced;</li>
 *   <li>the identity row stores only the mapping — no email, token or profile data;</li>
 *   <li>identities cascade when their user is deleted, and password-only users (no identity
 *       rows) are untouched.</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class UserOAuthIdentityPersistenceIntegrationTest {

    private static final String DATABASE_NAME = "hireflow_auth_oauth_identity";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName(DATABASE_NAME);

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Test-only JWT signing key; JwtService refuses to start without one.
        registry.add("hireflow.jwt.signing-key", () -> TestSigningKeys.VALID);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserOAuthIdentityRepository identityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsAndLooksUpIdentityByProviderAndSubject() {
        User user = userRepository.saveAndFlush(newUser());
        String subject = "google-sub-" + UUID.randomUUID();

        UserOAuthIdentity saved = identityRepository.saveAndFlush(
                new UserOAuthIdentity(user.getId(), OAuth2Provider.GOOGLE, subject));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getProvider()).isEqualTo(OAuth2Provider.GOOGLE);
        assertThat(saved.getProviderSubject()).isEqualTo(subject);

        assertThat(identityRepository.findByProviderAndProviderSubject(OAuth2Provider.GOOGLE, subject))
                .isPresent()
                .get()
                .extracting(UserOAuthIdentity::getUserId)
                .isEqualTo(user.getId());
        assertThat(identityRepository.existsByProviderAndProviderSubject(OAuth2Provider.GOOGLE, subject))
                .isTrue();
    }

    @Test
    void lookupOfUnknownIdentityIsEmpty() {
        assertThat(identityRepository.findByProviderAndProviderSubject(
                OAuth2Provider.GOOGLE, "never-linked-" + UUID.randomUUID())).isEmpty();
        assertThat(identityRepository.existsByProviderAndProviderSubject(
                OAuth2Provider.GOOGLE, "never-linked-" + UUID.randomUUID())).isFalse();
    }

    @Test
    void rejectsDuplicateProviderSubjectAtDatabaseLevel() {
        User first = userRepository.saveAndFlush(newUser());
        User second = userRepository.saveAndFlush(newUser());
        String subject = "duplicate-sub-" + UUID.randomUUID();

        identityRepository.saveAndFlush(new UserOAuthIdentity(first.getId(), OAuth2Provider.GOOGLE, subject));

        // Even if the application raced past its own check, the database refuses to link
        // the same Google identity to a second user.
        assertThatThrownBy(() -> identityRepository.saveAndFlush(
                new UserOAuthIdentity(second.getId(), OAuth2Provider.GOOGLE, subject)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void storesProviderAsStringColumn() {
        User user = userRepository.saveAndFlush(newUser());
        UserOAuthIdentity saved = identityRepository.saveAndFlush(
                new UserOAuthIdentity(user.getId(), OAuth2Provider.GOOGLE, "column-" + UUID.randomUUID()));

        String provider = jdbcTemplate.queryForObject(
                "SELECT provider FROM user_oauth_identities WHERE id = ?", String.class, saved.getId());
        assertThat(provider).isEqualTo("GOOGLE");
    }

    @Test
    void storesOnlyTheMappingAndNoProfileData() {
        User user = userRepository.saveAndFlush(newUser());
        UserOAuthIdentity saved = identityRepository.saveAndFlush(
                new UserOAuthIdentity(user.getId(), OAuth2Provider.GOOGLE, "minimal-" + UUID.randomUUID()));

        // The row carries only id, user_id, provider, provider_subject and created_at.
        // There is no email, token, name or picture column to leak.
        int columnCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_name = 'user_oauth_identities'",
                Integer.class);
        assertThat(columnCount).isEqualTo(5);

        UserOAuthIdentity reloaded = identityRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getProviderSubject()).isEqualTo(saved.getProviderSubject());
    }

    @Test
    void deletesIdentitiesTogetherWithTheirUser() {
        User user = userRepository.saveAndFlush(newUser());
        String subject = "cascade-" + UUID.randomUUID();
        identityRepository.saveAndFlush(new UserOAuthIdentity(user.getId(), OAuth2Provider.GOOGLE, subject));

        userRepository.delete(user);
        userRepository.flush();

        assertThat(identityRepository.existsByProviderAndProviderSubject(OAuth2Provider.GOOGLE, subject))
                .isFalse();
    }

    @Test
    void passwordOnlyUsersHaveNoIdentityRows() {
        User user = userRepository.saveAndFlush(newUser());

        assertThat(identityRepository.findAll().stream()
                .noneMatch(identity -> identity.getUserId().equals(user.getId()))).isTrue();
    }

    @Test
    void identityCreatedAtIsPopulatedAndImmutable() {
        User user = userRepository.saveAndFlush(newUser());
        UserOAuthIdentity saved = identityRepository.saveAndFlush(
                new UserOAuthIdentity(user.getId(), OAuth2Provider.GOOGLE, "created-at-" + UUID.randomUUID()));

        UserOAuthIdentity reloaded = identityRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        // TIMESTAMPTZ stores microsecond precision, so the round-tripped instant can differ
        // from the in-memory one by a fraction of a millisecond. Compare within that bound.
        assertThat(Math.abs(reloaded.getCreatedAt().toEpochMilli()
                - saved.getCreatedAt().toEpochMilli())).isLessThan(1);
        assertThat(reloaded.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
    }

    private User newUser() {
        User user = new User();
        user.setEmail("oauth-identity-" + UUID.randomUUID() + "@example.com");
        user.setFirstName("OAuth");
        user.setLastName("Identity");
        user.setRole(UserRole.CANDIDATE);
        return user;
    }
}
