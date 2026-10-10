package com.hireflow.auth;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.hireflow.auth.entity.User;
import com.hireflow.auth.entity.UserRole;
import com.hireflow.auth.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class UserPersistenceIntegrationTest {

    private static final String DATABASE_NAME = "hireflow_auth";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName(DATABASE_NAME);

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void persistsAndRetrievesUser() {
        User saved = userRepository.saveAndFlush(newUser("persist-" + UUID.randomUUID() + "@example.com"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isEqualTo(saved.getUpdatedAt());

        User found = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getEmail()).isEqualTo(saved.getEmail());
        assertThat(found.getFirstName()).isEqualTo("Jane");
        assertThat(found.getLastName()).isEqualTo("Doe");
        assertThat(found.getRole()).isEqualTo(UserRole.CANDIDATE);
        assertThat(found.isEnabled()).isTrue();
    }

    @Test
    void rejectsDuplicateEmailAtDatabaseLevel() {
        String email = "duplicate-" + UUID.randomUUID() + "@example.com";
        userRepository.saveAndFlush(newUser(email));

        User duplicate = newUser(email);
        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsRoleAsString() {
        User user = newUser("role-" + UUID.randomUUID() + "@example.com");
        user.setRole(UserRole.RECRUITER);
        User saved = userRepository.saveAndFlush(user);

        String role = jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE id = ?", String.class, saved.getId());
        assertThat(role).isEqualTo("RECRUITER");
    }

    @Test
    void updatesUserAndRefreshesUpdatedAt() {
        User saved = userRepository.saveAndFlush(newUser("update-" + UUID.randomUUID() + "@example.com"));
        Instant agedTimestamp = saved.getUpdatedAt().minusSeconds(300);

        User found = userRepository.findById(saved.getId()).orElseThrow();
        found.setFirstName("Updated");
        found.setUpdatedAt(agedTimestamp);
        userRepository.saveAndFlush(found);

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getFirstName()).isEqualTo("Updated");
        assertThat(reloaded.getUpdatedAt()).isAfter(agedTimestamp);
        assertThat(reloaded.getUpdatedAt().compareTo(reloaded.getCreatedAt())).isGreaterThanOrEqualTo(0);
    }

    @Test
    void persistsOnlyThePasswordHash() {
        User user = newUser("password-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("Sup3r-Secret!"));
        User saved = userRepository.saveAndFlush(user);

        String stored = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, saved.getId());
        assertThat(stored).startsWith("$2").isNotEqualTo("Sup3r-Secret!");

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getPasswordHash()).isEqualTo(saved.getPasswordHash());
    }

    @Test
    void keepsUserRowsCreatedBeforeThePasswordMigration() {
        // Simulates a row that existed before migration V2: password_hash stays NULL and
        // the user record remains fully readable, so no existing user data is broken.
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, first_name, last_name, role, enabled, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, now(), now())",
                id, "legacy-" + id + "@example.com", "Legacy", "User", "CANDIDATE", true);

        User reloaded = userRepository.findById(id).orElseThrow();

        assertThat(reloaded.getEmail()).isEqualTo("legacy-" + id + "@example.com");
        assertThat(reloaded.getPasswordHash()).isNull();
    }

    private User newUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Jane");
        user.setLastName("Doe");
        user.setRole(UserRole.CANDIDATE);
        return user;
    }
}
