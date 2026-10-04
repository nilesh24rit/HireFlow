package com.hireflow.application.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.hireflow.application.dto.ApplicationResponse;
import com.hireflow.application.dto.CreateApplicationRequest;
import com.hireflow.application.dto.UpdateApplicationStatusRequest;
import com.hireflow.application.entity.Application;
import com.hireflow.application.entity.ApplicationStatus;
import com.hireflow.application.exception.ApplicationNotFoundException;
import com.hireflow.application.exception.ApplicationValidationException;
import com.hireflow.application.exception.DuplicateApplicationException;
import com.hireflow.application.repository.ApplicationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    private ApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new ApplicationService(applicationRepository);
    }

    @Test
    void createsApplicationWithAppliedStatus() {
        UUID candidateId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        CreateApplicationRequest request = new CreateApplicationRequest(
                candidateId, jobId, "I would love to work on this role");
        when(applicationRepository.existsByCandidateIdAndJobId(candidateId, jobId)).thenReturn(false);
        stubSavedApplication();

        ApplicationResponse response = applicationService.createApplication(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.candidateId()).isEqualTo(candidateId);
        assertThat(response.jobId()).isEqualTo(jobId);
        assertThat(response.status()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(response.coverLetter()).isEqualTo("I would love to work on this role");
    }

    @Test
    void rejectsDuplicateApplicationBeforeSave() {
        UUID candidateId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        CreateApplicationRequest request = new CreateApplicationRequest(candidateId, jobId, null);
        when(applicationRepository.existsByCandidateIdAndJobId(candidateId, jobId)).thenReturn(true);

        assertThatThrownBy(() -> applicationService.createApplication(request))
                .isInstanceOf(DuplicateApplicationException.class)
                .hasMessageContaining(candidateId.toString())
                .hasMessageContaining(jobId.toString());

        verify(applicationRepository, never()).saveAndFlush(any(Application.class));
    }

    @Test
    void convertsUniqueConstraintViolationIntoDuplicateApplication() {
        UUID candidateId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        CreateApplicationRequest request = new CreateApplicationRequest(candidateId, jobId, null);
        when(applicationRepository.existsByCandidateIdAndJobId(candidateId, jobId)).thenReturn(false);
        when(applicationRepository.saveAndFlush(any(Application.class)))
                .thenThrow(new DataIntegrityViolationException("uk_applications_candidate_job"));

        assertThatThrownBy(() -> applicationService.createApplication(request))
                .isInstanceOf(DuplicateApplicationException.class);
    }

    @Test
    void rejectsCreateRequestWithoutCandidateOrJob() {
        CreateApplicationRequest missingCandidate = new CreateApplicationRequest(null, UUID.randomUUID(), null);
        CreateApplicationRequest missingJob = new CreateApplicationRequest(UUID.randomUUID(), null, null);

        assertThatThrownBy(() -> applicationService.createApplication(missingCandidate))
                .isInstanceOf(ApplicationValidationException.class)
                .hasMessageContaining("candidateId");
        assertThatThrownBy(() -> applicationService.createApplication(missingJob))
                .isInstanceOf(ApplicationValidationException.class)
                .hasMessageContaining("jobId");

        verify(applicationRepository, never()).saveAndFlush(any(Application.class));
    }

    @Test
    void findsApplicationById() {
        UUID id = UUID.randomUUID();
        when(applicationRepository.findById(id)).thenReturn(Optional.of(application(id)));

        ApplicationResponse response = applicationService.getApplicationById(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.status()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(response.coverLetter()).isEqualTo("I would love to work on this role");
    }

    @Test
    void throwsWhenApplicationNotFoundById() {
        UUID id = UUID.randomUUID();
        when(applicationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.getApplicationById(id))
                .isInstanceOf(ApplicationNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void findsApplicationsByCandidateId() {
        UUID candidateId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Application first = application(firstId);
        Application second = application(secondId);
        first.setCandidateId(candidateId);
        second.setCandidateId(candidateId);
        when(applicationRepository.findByCandidateId(candidateId)).thenReturn(List.of(first, second));

        List<ApplicationResponse> responses = applicationService.getApplicationsByCandidateId(candidateId);

        assertThat(responses)
                .extracting(ApplicationResponse::id)
                .containsExactly(firstId, secondId);
        assertThat(responses)
                .allSatisfy(response -> assertThat(response.candidateId()).isEqualTo(candidateId));
    }

    @Test
    void returnsEmptyListWhenCandidateHasNoApplications() {
        UUID candidateId = UUID.randomUUID();
        when(applicationRepository.findByCandidateId(candidateId)).thenReturn(List.of());

        assertThat(applicationService.getApplicationsByCandidateId(candidateId)).isEmpty();
    }

    @Test
    void findsApplicationsByJobId() {
        UUID jobId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Application application = application(id);
        application.setJobId(jobId);
        when(applicationRepository.findByJobId(jobId)).thenReturn(List.of(application));

        List<ApplicationResponse> responses = applicationService.getApplicationsByJobId(jobId);

        assertThat(responses).extracting(ApplicationResponse::id).containsExactly(id);
        assertThat(responses)
                .allSatisfy(response -> assertThat(response.jobId()).isEqualTo(jobId));
    }

    @Test
    void returnsEmptyListWhenJobHasNoApplications() {
        UUID jobId = UUID.randomUUID();
        when(applicationRepository.findByJobId(jobId)).thenReturn(List.of());

        assertThat(applicationService.getApplicationsByJobId(jobId)).isEmpty();
    }

    @Test
    void updatesApplicationStatus() {
        UUID id = UUID.randomUUID();
        Application application = application(id);
        when(applicationRepository.findById(id)).thenReturn(Optional.of(application));
        when(applicationRepository.saveAndFlush(application)).thenReturn(application);

        ApplicationResponse response = applicationService.updateApplicationStatus(
                id, new UpdateApplicationStatusRequest(ApplicationStatus.SHORTLISTED));

        assertThat(response.status()).isEqualTo(ApplicationStatus.SHORTLISTED);
        assertThat(response.coverLetter()).isEqualTo("I would love to work on this role");
        verify(applicationRepository).saveAndFlush(application);
    }

    @Test
    void rejectsStatusUpdateWithoutStatus() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> applicationService.updateApplicationStatus(
                id, new UpdateApplicationStatusRequest(null)))
                .isInstanceOf(ApplicationValidationException.class)
                .hasMessageContaining("status");

        verify(applicationRepository, never()).findById(id);
    }

    @Test
    void throwsWhenUpdatingStatusOfMissingApplication() {
        UUID id = UUID.randomUUID();
        when(applicationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.updateApplicationStatus(
                id, new UpdateApplicationStatusRequest(ApplicationStatus.REJECTED)))
                .isInstanceOf(ApplicationNotFoundException.class)
                .hasMessageContaining(id.toString());

        verify(applicationRepository, never()).saveAndFlush(any(Application.class));
    }

    @Test
    void deletesApplication() {
        UUID id = UUID.randomUUID();
        Application application = application(id);
        when(applicationRepository.findById(id)).thenReturn(Optional.of(application));

        applicationService.deleteApplication(id);

        verify(applicationRepository).delete(application);
    }

    @Test
    void throwsWhenDeletingMissingApplication() {
        UUID id = UUID.randomUUID();
        when(applicationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.deleteApplication(id))
                .isInstanceOf(ApplicationNotFoundException.class)
                .hasMessageContaining(id.toString());

        verify(applicationRepository, never()).delete(any(Application.class));
    }

    private void stubSavedApplication() {
        when(applicationRepository.saveAndFlush(any(Application.class))).thenAnswer(invocation -> {
            Application application = invocation.getArgument(0);
            ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
            return application;
        });
    }

    private Application application(UUID id) {
        Application application = new Application();
        ReflectionTestUtils.setField(application, "id", id);
        application.setCandidateId(UUID.randomUUID());
        application.setJobId(UUID.randomUUID());
        application.setStatus(ApplicationStatus.APPLIED);
        application.setCoverLetter("I would love to work on this role");
        return application;
    }
}
