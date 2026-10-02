package com.hireflow.candidate;

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

import com.hireflow.candidate.entity.Candidate;
import com.hireflow.candidate.entity.CandidateSkill;
import com.hireflow.candidate.repository.CandidateRepository;
import com.hireflow.candidate.repository.CandidateSkillRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class CandidatePersistenceIntegrationTest {

    private static final String DATABASE_NAME = "hireflow_candidate";

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
    private CandidateRepository candidateRepository;

    @Autowired
    private CandidateSkillRepository candidateSkillRepository;

    @Test
    void persistsAndRetrievesCandidateWithSkills() {
        Candidate saved = candidateRepository.saveAndFlush(newCandidate());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isEqualTo(saved.getUpdatedAt());

        candidateSkillRepository.saveAndFlush(newSkill(saved.getId(), "Java"));
        candidateSkillRepository.saveAndFlush(newSkill(saved.getId(), "Spring"));

        Candidate found = candidateRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getUserId()).isEqualTo(saved.getUserId());
        assertThat(found.getHeadline()).isEqualTo("Senior Java Developer");
        assertThat(found.getSummary()).isEqualTo("Experienced backend engineer");
        assertThat(found.getLocation()).isEqualTo("Berlin");
        assertThat(found.getYearsOfExperience()).isEqualTo(5);
        assertThat(found.getResumeUrl()).isEqualTo("https://resume.example.com/jane.pdf");

        List<CandidateSkill> skills = candidateSkillRepository.findByCandidateId(saved.getId());
        assertThat(skills).extracting(CandidateSkill::getSkill).containsExactly("Java", "Spring");
    }

    @Test
    void findsCandidateByUserId() {
        UUID userId = UUID.randomUUID();
        candidateRepository.saveAndFlush(newCandidate(userId));

        assertThat(candidateRepository.findByUserId(userId)).isPresent();
        assertThat(candidateRepository.existsByUserId(userId)).isTrue();
        assertThat(candidateRepository.findByUserId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void rejectsDuplicateUserIdAtDatabaseLevel() {
        UUID userId = UUID.randomUUID();
        candidateRepository.saveAndFlush(newCandidate(userId));

        assertThatThrownBy(() -> candidateRepository.saveAndFlush(newCandidate(userId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateSkillForSameCandidate() {
        UUID candidateId = candidateRepository.saveAndFlush(newCandidate()).getId();
        candidateSkillRepository.saveAndFlush(newSkill(candidateId, "Java"));

        assertThatThrownBy(() -> candidateSkillRepository.saveAndFlush(newSkill(candidateId, "Java")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsSkillCaseVariantsForSameCandidate() {
        UUID candidateId = candidateRepository.saveAndFlush(newCandidate()).getId();
        candidateSkillRepository.saveAndFlush(newSkill(candidateId, "Java"));

        assertThatThrownBy(() -> candidateSkillRepository.saveAndFlush(newSkill(candidateId, "java")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsBlankSkillAtDatabaseLevel() {
        UUID candidateId = candidateRepository.saveAndFlush(newCandidate()).getId();

        assertThatThrownBy(() -> candidateSkillRepository.saveAndFlush(newSkill(candidateId, "   ")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsSameSkillForDifferentCandidates() {
        Candidate first = candidateRepository.saveAndFlush(newCandidate());
        Candidate second = candidateRepository.saveAndFlush(newCandidate());
        candidateSkillRepository.saveAndFlush(newSkill(first.getId(), "Java"));
        candidateSkillRepository.saveAndFlush(newSkill(second.getId(), "Java"));

        assertThat(candidateSkillRepository.findByCandidateId(first.getId())).hasSize(1);
        assertThat(candidateSkillRepository.findByCandidateId(second.getId())).hasSize(1);
    }

    @Test
    void updatesCandidateAndRefreshesUpdatedAt() {
        Candidate saved = candidateRepository.saveAndFlush(newCandidate());
        Instant agedTimestamp = saved.getUpdatedAt().minusSeconds(300);

        Candidate found = candidateRepository.findById(saved.getId()).orElseThrow();
        found.setHeadline("Staff Engineer");
        found.setUpdatedAt(agedTimestamp);
        candidateRepository.saveAndFlush(found);

        Candidate reloaded = candidateRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getHeadline()).isEqualTo("Staff Engineer");
        assertThat(reloaded.getUpdatedAt()).isAfter(agedTimestamp);
        assertThat(reloaded.getCreatedAt().truncatedTo(ChronoUnit.MICROS))
                .isEqualTo(saved.getCreatedAt().truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    void deletesCandidateAndRemovesSkills() {
        Candidate saved = candidateRepository.saveAndFlush(newCandidate());
        candidateSkillRepository.saveAndFlush(newSkill(saved.getId(), "Java"));

        candidateRepository.delete(saved);
        candidateRepository.flush();

        assertThat(candidateRepository.findById(saved.getId())).isEmpty();
        assertThat(candidateSkillRepository.findByCandidateId(saved.getId())).isEmpty();
    }

    private Candidate newCandidate() {
        return newCandidate(UUID.randomUUID());
    }

    private Candidate newCandidate(UUID userId) {
        Candidate candidate = new Candidate();
        candidate.setUserId(userId);
        candidate.setHeadline("Senior Java Developer");
        candidate.setSummary("Experienced backend engineer");
        candidate.setLocation("Berlin");
        candidate.setYearsOfExperience(5);
        candidate.setCurrentCompany("Acme");
        candidate.setCurrentJobTitle("Software Engineer");
        candidate.setResumeUrl("https://resume.example.com/jane.pdf");
        candidate.setLinkedinUrl("https://linkedin.com/in/jane");
        candidate.setGithubUrl("https://github.com/jane");
        return candidate;
    }

    private CandidateSkill newSkill(UUID candidateId, String skill) {
        CandidateSkill candidateSkill = new CandidateSkill();
        candidateSkill.setCandidateId(candidateId);
        candidateSkill.setSkill(skill);
        return candidateSkill;
    }
}
