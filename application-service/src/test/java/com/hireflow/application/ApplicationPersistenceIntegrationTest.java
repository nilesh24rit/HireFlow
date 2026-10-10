package com.hireflow.application;

import com.hireflow.application.security.TestSigningKeys;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.hireflow.application.entity.Application;
import com.hireflow.application.entity.ApplicationStatus;
import com.hireflow.application.repository.ApplicationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class ApplicationPersistenceIntegrationTest {

    private static final String DATABASE_NAME = "hireflow_application";

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
    private ApplicationRepository applicationRepository;

    @Test
    void persistsAndRetrievesApplication() {
        UUID candidateId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Application saved = applicationRepository.saveAndFlush(newApplication(candidateId, jobId));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAppliedAt()).isNotNull();
        assertThat(saved.getAppliedAt()).isEqualTo(saved.getUpdatedAt());

        Application found = applicationRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getCandidateId()).isEqualTo(candidateId);
        assertThat(found.getJobId()).isEqualTo(jobId);
        assertThat(found.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(found.getCoverLetter()).isEqualTo("I would love to work on this role");
        assertThat(found.getAppliedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void rejectsDuplicateCandidateJobPair() {
        UUID candidateId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        applicationRepository.saveAndFlush(newApplication(candidateId, jobId));

        assertThatThrownBy(() -> applicationRepository.saveAndFlush(newApplication(candidateId, jobId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsSameCandidateForDifferentJobs() {
        UUID candidateId = UUID.randomUUID();
        applicationRepository.saveAndFlush(newApplication(candidateId, UUID.randomUUID()));
        applicationRepository.saveAndFlush(newApplication(candidateId, UUID.randomUUID()));

        assertThat(applicationRepository.findByCandidateId(candidateId)).hasSize(2);
    }

    @Test
    void findsApplicationsByCandidateId() {
        UUID candidateId = UUID.randomUUID();
        applicationRepository.saveAndFlush(newApplication(candidateId, UUID.randomUUID()));
        applicationRepository.saveAndFlush(newApplication(candidateId, UUID.randomUUID()));
        applicationRepository.saveAndFlush(newApplication(UUID.randomUUID(), UUID.randomUUID()));

        List<Application> found = applicationRepository.findByCandidateId(candidateId);
        assertThat(found).hasSize(2);
        assertThat(found).allSatisfy(application ->
                assertThat(application.getCandidateId()).isEqualTo(candidateId));
        assertThat(applicationRepository.findByCandidateId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findsApplicationsByJobId() {
        UUID jobId = UUID.randomUUID();
        applicationRepository.saveAndFlush(newApplication(UUID.randomUUID(), jobId));
        applicationRepository.saveAndFlush(newApplication(UUID.randomUUID(), jobId));
        applicationRepository.saveAndFlush(newApplication(UUID.randomUUID(), UUID.randomUUID()));

        List<Application> found = applicationRepository.findByJobId(jobId);
        assertThat(found).hasSize(2);
        assertThat(found).allSatisfy(application ->
                assertThat(application.getJobId()).isEqualTo(jobId));
        assertThat(applicationRepository.findByJobId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void detectsExistingCandidateJobPair() {
        UUID candidateId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        applicationRepository.saveAndFlush(newApplication(candidateId, jobId));

        assertThat(applicationRepository.existsByCandidateIdAndJobId(candidateId, jobId)).isTrue();
        assertThat(applicationRepository.existsByCandidateIdAndJobId(candidateId, UUID.randomUUID()))
                .isFalse();
    }

    @Test
    void updatesStatusAndRefreshesUpdatedAt() {
        Application saved = applicationRepository.saveAndFlush(
                newApplication(UUID.randomUUID(), UUID.randomUUID()));
        Instant agedTimestamp = saved.getUpdatedAt().minusSeconds(300);

        Application found = applicationRepository.findById(saved.getId()).orElseThrow();
        found.setStatus(ApplicationStatus.SHORTLISTED);
        found.setUpdatedAt(agedTimestamp);
        applicationRepository.saveAndFlush(found);

        Application reloaded = applicationRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
        assertThat(reloaded.getUpdatedAt()).isAfter(agedTimestamp);
        assertThat(reloaded.getAppliedAt())
                .isCloseTo(saved.getAppliedAt(), within(1, ChronoUnit.MICROS));
    }

    @Test
    void deletesApplication() {
        Application saved = applicationRepository.saveAndFlush(
                newApplication(UUID.randomUUID(), UUID.randomUUID()));

        applicationRepository.delete(saved);
        applicationRepository.flush();

        assertThat(applicationRepository.findById(saved.getId())).isEmpty();
    }

    private Application newApplication(UUID candidateId, UUID jobId) {
        Application application = new Application();
        application.setCandidateId(candidateId);
        application.setJobId(jobId);
        application.setStatus(ApplicationStatus.APPLIED);
        application.setCoverLetter("I would love to work on this role");
        return application;
    }
}
