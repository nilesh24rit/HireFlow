package com.hireflow.candidate.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.hireflow.candidate.dto.CreateCandidateRequest;
import com.hireflow.candidate.dto.UpdateCandidateRequest;
import com.hireflow.candidate.dto.CandidateResponse;
import com.hireflow.candidate.entity.Candidate;
import com.hireflow.candidate.entity.CandidateSkill;
import com.hireflow.candidate.exception.DuplicateResourceException;
import com.hireflow.candidate.exception.ResourceNotFoundException;
import com.hireflow.candidate.repository.CandidateRepository;
import com.hireflow.candidate.repository.CandidateSkillRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateServiceTest {

    @Mock
    private CandidateRepository candidateRepository;

    @Mock
    private CandidateSkillRepository candidateSkillRepository;

    @Captor
    private ArgumentCaptor<List<CandidateSkill>> skillsCaptor;

    private CandidateService candidateService;

    @BeforeEach
    void setUp() {
        candidateService = new CandidateService(candidateRepository, candidateSkillRepository);
    }

    @Test
    void createsCandidateWithSkills() {
        UUID userId = UUID.randomUUID();
        CreateCandidateRequest request = createRequest(userId, List.of("Java", "Spring"));
        when(candidateRepository.existsByUserId(userId)).thenReturn(false);
        when(candidateRepository.saveAndFlush(any(Candidate.class))).thenAnswer(invocation -> {
            Candidate candidate = invocation.getArgument(0);
            ReflectionTestUtils.setField(candidate, "id", UUID.randomUUID());
            return candidate;
        });
        when(candidateSkillRepository.findByCandidateId(any(UUID.class)))
                .thenReturn(List.of(skill("Java"), skill("Spring")));

        CandidateResponse response = candidateService.createCandidate(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.headline()).isEqualTo("Senior Java Developer");
        assertThat(response.location()).isEqualTo("Berlin");
        assertThat(response.yearsOfExperience()).isEqualTo(5);
        assertThat(response.currentCompany()).isEqualTo("Acme");
        assertThat(response.skills()).containsExactly("Java", "Spring");

        verify(candidateSkillRepository).saveAll(skillsCaptor.capture());
        assertThat(skillsCaptor.getValue())
                .extracting(CandidateSkill::getSkill)
                .containsExactly("Java", "Spring");
        assertThat(skillsCaptor.getValue())
                .allSatisfy(skill -> assertThat(skill.getCandidateId()).isEqualTo(response.id()));
        verify(candidateSkillRepository).flush();
    }

    @Test
    void normalizesSkillsOnCreate() {
        UUID userId = UUID.randomUUID();
        CreateCandidateRequest request = createRequest(userId, List.of("  Java  ", "java", "JAVA", " ", "Python"));
        when(candidateRepository.existsByUserId(userId)).thenReturn(false);
        when(candidateRepository.saveAndFlush(any(Candidate.class))).thenAnswer(invocation -> {
            Candidate candidate = invocation.getArgument(0);
            ReflectionTestUtils.setField(candidate, "id", UUID.randomUUID());
            return candidate;
        });
        when(candidateSkillRepository.findByCandidateId(any(UUID.class))).thenReturn(List.of());

        candidateService.createCandidate(request);

        verify(candidateSkillRepository).saveAll(skillsCaptor.capture());
        assertThat(skillsCaptor.getValue())
                .extracting(CandidateSkill::getSkill)
                .containsExactly("Java", "Python");
    }

    @Test
    void rejectsDuplicateUserIdBeforeSaving() {
        UUID userId = UUID.randomUUID();
        CreateCandidateRequest request = createRequest(userId, List.of());
        when(candidateRepository.existsByUserId(userId)).thenReturn(true);

        assertThatThrownBy(() -> candidateService.createCandidate(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining(userId.toString());

        verify(candidateRepository, never()).saveAndFlush(any(Candidate.class));
    }

    @Test
    void rejectsDuplicateUserWhenDatabaseConstraintFails() {
        UUID userId = UUID.randomUUID();
        CreateCandidateRequest request = createRequest(userId, List.of());
        when(candidateRepository.existsByUserId(userId)).thenReturn(false);
        when(candidateRepository.saveAndFlush(any(Candidate.class)))
                .thenThrow(new DataIntegrityViolationException("uk_candidates_user_id"));

        assertThatThrownBy(() -> candidateService.createCandidate(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining(userId.toString());
    }

    @Test
    void findsCandidateById() {
        UUID id = UUID.randomUUID();
        when(candidateRepository.findById(id)).thenReturn(Optional.of(candidate(id)));
        when(candidateSkillRepository.findByCandidateId(id))
                .thenReturn(List.of(skill("Java"), skill("Python")));

        CandidateResponse response = candidateService.getCandidateById(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.headline()).isEqualTo("Senior Java Developer");
        assertThat(response.githubUrl()).isEqualTo("https://github.com/jane");
        assertThat(response.skills()).containsExactly("Java", "Python");
    }

    @Test
    void throwsWhenCandidateNotFoundById() {
        UUID id = UUID.randomUUID();
        when(candidateRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> candidateService.getCandidateById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void findsCandidateByUserId() {
        UUID id = UUID.randomUUID();
        Candidate candidate = candidate(id);
        when(candidateRepository.findByUserId(candidate.getUserId())).thenReturn(Optional.of(candidate));
        when(candidateSkillRepository.findByCandidateId(id)).thenReturn(List.of());

        CandidateResponse response = candidateService.getCandidateByUserId(candidate.getUserId());

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.userId()).isEqualTo(candidate.getUserId());
    }

    @Test
    void throwsWhenCandidateNotFoundByUserId() {
        UUID userId = UUID.randomUUID();
        when(candidateRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> candidateService.getCandidateByUserId(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(userId.toString());
    }

    @Test
    void updatesCandidateProfileFields() {
        UUID id = UUID.randomUUID();
        Candidate candidate = candidate(id);
        when(candidateRepository.findById(id)).thenReturn(Optional.of(candidate));
        when(candidateRepository.saveAndFlush(candidate)).thenReturn(candidate);
        when(candidateSkillRepository.findByCandidateId(id)).thenReturn(List.of(skill("Java")));

        UpdateCandidateRequest request = new UpdateCandidateRequest(
                "Staff Engineer", null, "Munich", 7, null, null, null, null, null, null);

        CandidateResponse response = candidateService.updateCandidate(id, request);

        assertThat(response.headline()).isEqualTo("Staff Engineer");
        assertThat(response.location()).isEqualTo("Munich");
        assertThat(response.yearsOfExperience()).isEqualTo(7);
        assertThat(candidate.getSummary()).isEqualTo("Experienced backend engineer");
        assertThat(candidate.getCurrentCompany()).isEqualTo("Acme");
        assertThat(response.skills()).containsExactly("Java");
        verify(candidateSkillRepository, never()).deleteByCandidateId(any(UUID.class));
    }

    @Test
    void replacesSkillsOnUpdate() {
        UUID id = UUID.randomUUID();
        Candidate candidate = candidate(id);
        when(candidateRepository.findById(id)).thenReturn(Optional.of(candidate));
        when(candidateRepository.saveAndFlush(candidate)).thenReturn(candidate);
        when(candidateSkillRepository.findByCandidateId(id))
                .thenReturn(List.of(skill("Docker"), skill("Kafka")));

        UpdateCandidateRequest request = new UpdateCandidateRequest(
                null, null, null, null, null, null, null, null, null,
                List.of(" Kafka ", "kafka", "Docker"));

        CandidateResponse response = candidateService.updateCandidate(id, request);

        verify(candidateSkillRepository).deleteByCandidateId(id);
        verify(candidateSkillRepository).saveAll(skillsCaptor.capture());
        assertThat(skillsCaptor.getValue())
                .extracting(CandidateSkill::getSkill)
                .containsExactly("Kafka", "Docker");
        assertThat(response.skills()).containsExactly("Docker", "Kafka");
    }

    @Test
    void clearsSkillsWhenEmptyListProvidedOnUpdate() {
        UUID id = UUID.randomUUID();
        Candidate candidate = candidate(id);
        when(candidateRepository.findById(id)).thenReturn(Optional.of(candidate));
        when(candidateRepository.saveAndFlush(candidate)).thenReturn(candidate);

        UpdateCandidateRequest request = new UpdateCandidateRequest(
                null, null, null, null, null, null, null, null, null, List.of());

        candidateService.updateCandidate(id, request);

        verify(candidateSkillRepository).deleteByCandidateId(id);
        verify(candidateSkillRepository).flush();
        verify(candidateSkillRepository, never()).saveAll(any());
    }

    @Test
    void throwsWhenUpdatingMissingCandidate() {
        UUID id = UUID.randomUUID();
        when(candidateRepository.findById(id)).thenReturn(Optional.empty());

        UpdateCandidateRequest request = new UpdateCandidateRequest(
                "Staff Engineer", null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> candidateService.updateCandidate(id, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void deletesCandidate() {
        UUID id = UUID.randomUUID();
        Candidate candidate = candidate(id);
        when(candidateRepository.findById(id)).thenReturn(Optional.of(candidate));

        candidateService.deleteCandidate(id);

        verify(candidateRepository).delete(candidate);
    }

    @Test
    void throwsWhenDeletingMissingCandidate() {
        UUID id = UUID.randomUUID();
        when(candidateRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> candidateService.deleteCandidate(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
        verify(candidateRepository, never()).delete(any(Candidate.class));
    }

    private CreateCandidateRequest createRequest(UUID userId, List<String> skills) {
        return new CreateCandidateRequest(
                userId,
                "Senior Java Developer",
                "Experienced backend engineer",
                "Berlin",
                5,
                "Acme",
                "Software Engineer",
                "https://resume.example.com/jane.pdf",
                "https://linkedin.com/in/jane",
                "https://github.com/jane",
                skills);
    }

    private Candidate candidate(UUID id) {
        Candidate candidate = new Candidate();
        ReflectionTestUtils.setField(candidate, "id", id);
        candidate.setUserId(UUID.randomUUID());
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

    private CandidateSkill skill(String skill) {
        CandidateSkill candidateSkill = new CandidateSkill();
        candidateSkill.setSkill(skill);
        return candidateSkill;
    }
}
