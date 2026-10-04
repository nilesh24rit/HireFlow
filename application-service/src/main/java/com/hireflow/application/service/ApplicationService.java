package com.hireflow.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hireflow.application.dto.ApplicationResponse;
import com.hireflow.application.dto.CreateApplicationRequest;
import com.hireflow.application.dto.UpdateApplicationStatusRequest;
import com.hireflow.application.entity.Application;
import com.hireflow.application.entity.ApplicationStatus;
import com.hireflow.application.exception.ApplicationNotFoundException;
import com.hireflow.application.exception.ApplicationValidationException;
import com.hireflow.application.exception.DuplicateApplicationException;
import com.hireflow.application.repository.ApplicationRepository;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;

    public ApplicationService(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @Transactional
    public ApplicationResponse createApplication(CreateApplicationRequest request) {
        if (request.candidateId() == null || request.jobId() == null) {
            throw new ApplicationValidationException("candidateId and jobId must not be null");
        }
        if (applicationRepository.existsByCandidateIdAndJobId(request.candidateId(), request.jobId())) {
            throw duplicateApplication(request.candidateId(), request.jobId());
        }

        Application application = new Application();
        application.setCandidateId(request.candidateId());
        application.setJobId(request.jobId());
        application.setCoverLetter(request.coverLetter());
        application.setStatus(ApplicationStatus.APPLIED);

        Application saved;
        try {
            saved = applicationRepository.saveAndFlush(application);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateApplication(request.candidateId(), request.jobId());
        }
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getApplicationById(UUID id) {
        return toResponse(findApplication(id));
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getApplicationsByCandidateId(UUID candidateId) {
        return applicationRepository.findByCandidateId(candidateId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getApplicationsByJobId(UUID jobId) {
        return applicationRepository.findByJobId(jobId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ApplicationResponse updateApplicationStatus(UUID id, UpdateApplicationStatusRequest request) {
        if (request.status() == null) {
            throw new ApplicationValidationException("status must not be null");
        }
        Application application = findApplication(id);
        application.setStatus(request.status());
        return toResponse(applicationRepository.saveAndFlush(application));
    }

    @Transactional
    public void deleteApplication(UUID id) {
        applicationRepository.delete(findApplication(id));
    }

    private Application findApplication(UUID id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException(
                        "No application found with id '" + id + "'"));
    }

    private DuplicateApplicationException duplicateApplication(UUID candidateId, UUID jobId) {
        return new DuplicateApplicationException(
                "Candidate '" + candidateId + "' has already applied to job '" + jobId + "'");
    }

    private ApplicationResponse toResponse(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getCandidateId(),
                application.getJobId(),
                application.getStatus(),
                application.getCoverLetter(),
                application.getAppliedAt(),
                application.getUpdatedAt());
    }
}
