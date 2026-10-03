package com.hireflow.job.service;

import java.util.Arrays;
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

import com.hireflow.job.dto.CreateJobRequest;
import com.hireflow.job.dto.JobResponse;
import com.hireflow.job.dto.UpdateJobRequest;
import com.hireflow.job.entity.EmploymentType;
import com.hireflow.job.entity.Job;
import com.hireflow.job.entity.JobSkill;
import com.hireflow.job.entity.JobStatus;
import com.hireflow.job.exception.DuplicateJobSkillException;
import com.hireflow.job.exception.JobNotFoundException;
import com.hireflow.job.exception.JobValidationException;
import com.hireflow.job.repository.JobRepository;
import com.hireflow.job.repository.JobSkillRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobSkillRepository jobSkillRepository;

    @Captor
    private ArgumentCaptor<List<JobSkill>> skillsCaptor;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobService(jobRepository, jobSkillRepository);
    }

    @Test
    void createsJobWithSkills() {
        UUID recruiterId = UUID.randomUUID();
        CreateJobRequest request = createRequest(recruiterId, List.of("Java", "Spring"));
        stubSavedJob();
        when(jobSkillRepository.findByJobId(any(UUID.class)))
                .thenReturn(List.of(skill("Java"), skill("Spring")));

        JobResponse response = jobService.createJob(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.recruiterId()).isEqualTo(recruiterId);
        assertThat(response.title()).isEqualTo("Senior Java Developer");
        assertThat(response.description()).isEqualTo("Build and maintain Spring Boot services");
        assertThat(response.location()).isEqualTo("Berlin");
        assertThat(response.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(response.experienceMin()).isEqualTo(3);
        assertThat(response.experienceMax()).isEqualTo(6);
        assertThat(response.salaryMin()).isEqualTo(60000);
        assertThat(response.salaryMax()).isEqualTo(90000);
        assertThat(response.status()).isEqualTo(JobStatus.DRAFT);
        assertThat(response.skills()).containsExactly("Java", "Spring");

        verify(jobSkillRepository).saveAll(skillsCaptor.capture());
        assertThat(skillsCaptor.getValue())
                .extracting(JobSkill::getSkill)
                .containsExactly("Java", "Spring");
        assertThat(skillsCaptor.getValue())
                .allSatisfy(skill -> assertThat(skill.getJobId()).isEqualTo(response.id()));
        verify(jobSkillRepository).flush();
    }

    @Test
    void createsJobWithRequestedStatus() {
        UUID recruiterId = UUID.randomUUID();
        CreateJobRequest request = new CreateJobRequest(
                recruiterId, "Senior Java Developer", "Build and maintain Spring Boot services",
                "Berlin", EmploymentType.FULL_TIME, 3, 6, 60000, 90000, JobStatus.OPEN, List.of());
        stubSavedJob();
        when(jobSkillRepository.findByJobId(any(UUID.class))).thenReturn(List.of());

        JobResponse response = jobService.createJob(request);

        assertThat(response.status()).isEqualTo(JobStatus.OPEN);
        verify(jobSkillRepository, never()).saveAll(any());
    }

    @Test
    void normalizesSkillsOnCreate() {
        UUID recruiterId = UUID.randomUUID();
        CreateJobRequest request = createRequest(recruiterId,
                Arrays.asList("  Java  ", "java", "JAVA", " ", null, "Python"));
        stubSavedJob();
        when(jobSkillRepository.findByJobId(any(UUID.class))).thenReturn(List.of());

        jobService.createJob(request);

        verify(jobSkillRepository).saveAll(skillsCaptor.capture());
        assertThat(skillsCaptor.getValue())
                .extracting(JobSkill::getSkill)
                .containsExactly("Java", "Python");
    }

    @Test
    void rejectsInconsistentExperienceRangeOnCreate() {
        UUID recruiterId = UUID.randomUUID();
        CreateJobRequest request = new CreateJobRequest(
                recruiterId, "Senior Java Developer", "Build and maintain Spring Boot services",
                "Berlin", EmploymentType.FULL_TIME, 10, 5, null, null, null, null);

        assertThatThrownBy(() -> jobService.createJob(request))
                .isInstanceOf(JobValidationException.class)
                .hasMessageContaining("experienceMin");

        verify(jobRepository, never()).saveAndFlush(any(Job.class));
    }

    @Test
    void findsJobById() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.of(job(id)));
        when(jobSkillRepository.findByJobId(id))
                .thenReturn(List.of(skill("Java"), skill("Kafka")));

        JobResponse response = jobService.getJobById(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.title()).isEqualTo("Senior Java Developer");
        assertThat(response.status()).isEqualTo(JobStatus.OPEN);
        assertThat(response.skills()).containsExactly("Java", "Kafka");
    }

    @Test
    void throwsWhenJobNotFoundById() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.getJobById(id))
                .isInstanceOf(JobNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void findsJobsByRecruiterId() {
        UUID recruiterId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Job first = job(firstId);
        Job second = job(secondId);
        second.setTitle("Backend Engineer");
        first.setRecruiterId(recruiterId);
        second.setRecruiterId(recruiterId);
        when(jobRepository.findByRecruiterId(recruiterId)).thenReturn(List.of(first, second));
        when(jobSkillRepository.findByJobId(any(UUID.class))).thenReturn(List.of());

        List<JobResponse> responses = jobService.getJobsByRecruiterId(recruiterId);

        assertThat(responses)
                .extracting(JobResponse::id)
                .containsExactly(firstId, secondId);
        assertThat(responses)
                .allSatisfy(response -> assertThat(response.recruiterId()).isEqualTo(recruiterId));
    }

    @Test
    void returnsEmptyListWhenRecruiterHasNoJobs() {
        UUID recruiterId = UUID.randomUUID();
        when(jobRepository.findByRecruiterId(recruiterId)).thenReturn(List.of());

        assertThat(jobService.getJobsByRecruiterId(recruiterId)).isEmpty();
    }

    @Test
    void updatesJobFields() {
        UUID id = UUID.randomUUID();
        Job job = job(id);
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        when(jobRepository.saveAndFlush(job)).thenReturn(job);
        when(jobSkillRepository.findByJobId(id)).thenReturn(List.of(skill("Java")));

        UpdateJobRequest request = new UpdateJobRequest(
                "Staff Engineer", null, "Munich", null, null, null, null, null, JobStatus.OPEN, null);

        JobResponse response = jobService.updateJob(id, request);

        assertThat(response.title()).isEqualTo("Staff Engineer");
        assertThat(response.location()).isEqualTo("Munich");
        assertThat(response.status()).isEqualTo(JobStatus.OPEN);
        assertThat(response.description()).isEqualTo("Build and maintain Spring Boot services");
        assertThat(response.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(response.experienceMin()).isEqualTo(3);
        assertThat(response.skills()).containsExactly("Java");
        verify(jobSkillRepository, never()).deleteByJobId(any(UUID.class));
    }

    @Test
    void replacesSkillsOnUpdate() {
        UUID id = UUID.randomUUID();
        Job job = job(id);
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        when(jobRepository.saveAndFlush(job)).thenReturn(job);
        when(jobSkillRepository.findByJobId(id))
                .thenReturn(List.of(skill("Docker"), skill("Kafka")));

        UpdateJobRequest request = new UpdateJobRequest(
                null, null, null, null, null, null, null, null, null,
                List.of(" Kafka ", "kafka", "Docker"));

        JobResponse response = jobService.updateJob(id, request);

        verify(jobSkillRepository).deleteByJobId(id);
        verify(jobSkillRepository).saveAll(skillsCaptor.capture());
        assertThat(skillsCaptor.getValue())
                .extracting(JobSkill::getSkill)
                .containsExactly("Kafka", "Docker");
        assertThat(response.skills()).containsExactly("Docker", "Kafka");
    }

    @Test
    void clearsSkillsWhenEmptyListProvidedOnUpdate() {
        UUID id = UUID.randomUUID();
        Job job = job(id);
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        when(jobRepository.saveAndFlush(job)).thenReturn(job);
        when(jobSkillRepository.findByJobId(id)).thenReturn(List.of());

        UpdateJobRequest request = new UpdateJobRequest(
                null, null, null, null, null, null, null, null, null, List.of());

        jobService.updateJob(id, request);

        verify(jobSkillRepository).deleteByJobId(id);
        verify(jobSkillRepository).flush();
        verify(jobSkillRepository, never()).saveAll(any());
    }

    @Test
    void rejectsInconsistentMergedRangeOnUpdate() {
        UUID id = UUID.randomUUID();
        Job job = job(id);
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));

        UpdateJobRequest request = new UpdateJobRequest(
                null, null, null, null, 10, null, null, null, null, null);

        assertThatThrownBy(() -> jobService.updateJob(id, request))
                .isInstanceOf(JobValidationException.class)
                .hasMessageContaining("experienceMin");

        verify(jobRepository, never()).saveAndFlush(any(Job.class));
    }

    @Test
    void rejectsBlankTitleOnUpdate() {
        UUID id = UUID.randomUUID();
        Job job = job(id);
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));

        UpdateJobRequest request = new UpdateJobRequest(
                "   ", null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> jobService.updateJob(id, request))
                .isInstanceOf(JobValidationException.class)
                .hasMessageContaining("title");

        verify(jobRepository, never()).saveAndFlush(any(Job.class));
    }

    @Test
    void throwsWhenUpdatingMissingJob() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        UpdateJobRequest request = new UpdateJobRequest(
                "Staff Engineer", null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> jobService.updateJob(id, request))
                .isInstanceOf(JobNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void deletesJob() {
        UUID id = UUID.randomUUID();
        Job job = job(id);
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));

        jobService.deleteJob(id);

        verify(jobRepository).delete(job);
    }

    @Test
    void throwsWhenDeletingMissingJob() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.deleteJob(id))
                .isInstanceOf(JobNotFoundException.class)
                .hasMessageContaining(id.toString());
        verify(jobRepository, never()).delete(any(Job.class));
    }

    @Test
    void convertsDuplicateSkillViolationIntoDuplicateJobSkillException() {
        UUID recruiterId = UUID.randomUUID();
        CreateJobRequest request = createRequest(recruiterId, List.of("Java"));
        stubSavedJob();
        when(jobSkillRepository.saveAll(any()))
                .thenThrow(new DataIntegrityViolationException("uk_job_skills_job_skill"));

        assertThatThrownBy(() -> jobService.createJob(request))
                .isInstanceOf(DuplicateJobSkillException.class);
    }

    private void stubSavedJob() {
        when(jobRepository.saveAndFlush(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "id", UUID.randomUUID());
            return job;
        });
    }

    private CreateJobRequest createRequest(UUID recruiterId, List<String> skills) {
        return new CreateJobRequest(
                recruiterId,
                "Senior Java Developer",
                "Build and maintain Spring Boot services",
                "Berlin",
                EmploymentType.FULL_TIME,
                3,
                6,
                60000,
                90000,
                null,
                skills);
    }

    private Job job(UUID id) {
        Job job = new Job();
        ReflectionTestUtils.setField(job, "id", id);
        job.setRecruiterId(UUID.randomUUID());
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

    private JobSkill skill(String skill) {
        JobSkill jobSkill = new JobSkill();
        jobSkill.setSkill(skill);
        return jobSkill;
    }
}
