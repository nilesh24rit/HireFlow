package com.hireflow.job;

import com.hireflow.job.security.TestSigningKeys;

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

import com.hireflow.job.entity.EmploymentType;
import com.hireflow.job.entity.Job;
import com.hireflow.job.entity.JobSkill;
import com.hireflow.job.entity.JobStatus;
import com.hireflow.job.repository.JobRepository;
import com.hireflow.job.repository.JobSkillRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class JobPersistenceIntegrationTest {

    private static final String DATABASE_NAME = "hireflow_job";

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
    private JobRepository jobRepository;

    @Autowired
    private JobSkillRepository jobSkillRepository;

    @Test
    void persistsAndRetrievesJobWithSkills() {
        Job saved = jobRepository.saveAndFlush(newJob(UUID.randomUUID()));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isEqualTo(saved.getUpdatedAt());

        jobSkillRepository.saveAndFlush(newSkill(saved.getId(), "Java"));
        jobSkillRepository.saveAndFlush(newSkill(saved.getId(), "Spring"));

        Job found = jobRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getRecruiterId()).isEqualTo(saved.getRecruiterId());
        assertThat(found.getTitle()).isEqualTo("Senior Java Developer");
        assertThat(found.getDescription()).isEqualTo("Build and maintain Spring Boot services");
        assertThat(found.getLocation()).isEqualTo("Berlin");
        assertThat(found.getEmploymentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(found.getExperienceMin()).isEqualTo(3);
        assertThat(found.getExperienceMax()).isEqualTo(6);
        assertThat(found.getSalaryMin()).isEqualTo(60000);
        assertThat(found.getSalaryMax()).isEqualTo(90000);
        assertThat(found.getStatus()).isEqualTo(JobStatus.OPEN);

        List<JobSkill> skills = jobSkillRepository.findByJobId(saved.getId());
        assertThat(skills).extracting(JobSkill::getSkill).containsExactly("Java", "Spring");
    }

    @Test
    void findsJobsByRecruiterId() {
        UUID recruiterId = UUID.randomUUID();
        jobRepository.saveAndFlush(newJob(recruiterId));
        jobRepository.saveAndFlush(newJob(recruiterId));
        jobRepository.saveAndFlush(newJob(UUID.randomUUID()));

        assertThat(jobRepository.findByRecruiterId(recruiterId)).hasSize(2);
        assertThat(jobRepository.findByRecruiterId(UUID.randomUUID())).isEmpty();
        assertThat(jobRepository.existsById(jobRepository.findByRecruiterId(recruiterId).get(0).getId()))
                .isTrue();
    }

    @Test
    void rejectsDuplicateSkillForSameJob() {
        UUID jobId = jobRepository.saveAndFlush(newJob(UUID.randomUUID())).getId();
        jobSkillRepository.saveAndFlush(newSkill(jobId, "Java"));

        assertThatThrownBy(() -> jobSkillRepository.saveAndFlush(newSkill(jobId, "Java")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsSkillCaseVariantsForSameJob() {
        UUID jobId = jobRepository.saveAndFlush(newJob(UUID.randomUUID())).getId();
        jobSkillRepository.saveAndFlush(newSkill(jobId, "Java"));

        assertThatThrownBy(() -> jobSkillRepository.saveAndFlush(newSkill(jobId, "java")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsBlankSkillAtDatabaseLevel() {
        UUID jobId = jobRepository.saveAndFlush(newJob(UUID.randomUUID())).getId();

        assertThatThrownBy(() -> jobSkillRepository.saveAndFlush(newSkill(jobId, "   ")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsSameSkillForDifferentJobs() {
        UUID firstJobId = jobRepository.saveAndFlush(newJob(UUID.randomUUID())).getId();
        UUID secondJobId = jobRepository.saveAndFlush(newJob(UUID.randomUUID())).getId();
        jobSkillRepository.saveAndFlush(newSkill(firstJobId, "Java"));
        jobSkillRepository.saveAndFlush(newSkill(secondJobId, "Java"));

        assertThat(jobSkillRepository.findByJobId(firstJobId)).hasSize(1);
        assertThat(jobSkillRepository.findByJobId(secondJobId)).hasSize(1);
    }

    @Test
    void rejectsInconsistentRangesAtDatabaseLevel() {
        Job invalid = newJob(UUID.randomUUID());
        invalid.setExperienceMin(10);
        invalid.setExperienceMax(2);

        assertThatThrownBy(() -> jobRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updatesJobAndRefreshesUpdatedAt() {
        Job saved = jobRepository.saveAndFlush(newJob(UUID.randomUUID()));
        Instant agedTimestamp = saved.getUpdatedAt().minusSeconds(300);

        Job found = jobRepository.findById(saved.getId()).orElseThrow();
        found.setTitle("Staff Engineer");
        found.setUpdatedAt(agedTimestamp);
        jobRepository.saveAndFlush(found);

        Job reloaded = jobRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Staff Engineer");
        assertThat(reloaded.getUpdatedAt()).isAfter(agedTimestamp);
        assertThat(reloaded.getCreatedAt())
                .isCloseTo(saved.getCreatedAt(), within(1, ChronoUnit.MICROS));
    }

    @Test
    void deletesJobAndRemovesSkills() {
        Job saved = jobRepository.saveAndFlush(newJob(UUID.randomUUID()));
        jobSkillRepository.saveAndFlush(newSkill(saved.getId(), "Java"));

        jobRepository.delete(saved);
        jobRepository.flush();

        assertThat(jobRepository.findById(saved.getId())).isEmpty();
        assertThat(jobSkillRepository.findByJobId(saved.getId())).isEmpty();
    }

    private Job newJob(UUID recruiterId) {
        Job job = new Job();
        job.setRecruiterId(recruiterId);
        job.setTitle("Senior Java Developer");
        job.setDescription("Build and maintain Spring Boot services");
        job.setLocation("Berlin");
        job.setEmploymentType(EmploymentType.FULL_TIME);
        job.setExperienceMin(3);
        job.setExperienceMax(6);
        job.setSalaryMin(60000);
        job.setSalaryMax(90000);
        job.setStatus(JobStatus.OPEN);
        return job;
    }

    private JobSkill newSkill(UUID jobId, String skill) {
        JobSkill jobSkill = new JobSkill();
        jobSkill.setJobId(jobId);
        jobSkill.setSkill(skill);
        return jobSkill;
    }
}
