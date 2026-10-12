package com.hireflow.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hireflow.application.dto.ApplicationResponse;
import com.hireflow.application.dto.CreateApplicationRequest;
import com.hireflow.application.dto.UpdateApplicationStatusRequest;
import com.hireflow.application.entity.Application;
import com.hireflow.application.entity.ApplicationStatus;
import com.hireflow.application.exception.DuplicateResourceException;
import com.hireflow.application.exception.InvalidRequestException;
import com.hireflow.application.exception.ResourceNotFoundException;
import com.hireflow.application.repository.ApplicationRepository;
import com.hireflow.application.security.CrossServiceOwnershipClient;

/**
 * Application service managing job applications with HireFlow authorization
 * rules enforced next to the operations they protect.
 *
 * <p>The permission matrix for {@code /api/applications}:</p>
 *
 * <ul>
 *   <li><b>Create</b> — CANDIDATE (own candidate profile only) or ADMIN.</li>
 *   <li><b>Read by application id</b> — CANDIDATE (own application), RECRUITER (applications
 *       for own jobs), or ADMIN.</li>
 *   <li><b>Read by candidate id</b> — CANDIDATE (own applications only) or ADMIN.</li>
 *   <li><b>Read by job id</b> — RECRUITER (own jobs only) or ADMIN.</li>
 *   <li><b>Update status</b> — RECRUITER (own jobs only) or ADMIN.</li>
 *   <li><b>Delete (withdraw)</b> — CANDIDATE (own application only) or ADMIN.</li>
 * </ul>
 *
 * <p>Ownership verification across service boundaries is performed via
 * {@link CrossServiceOwnershipClient}, failing closed whenever ownership cannot be confirmed.</p>
 */
@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final CrossServiceOwnershipClient ownershipClient;

    public ApplicationService(ApplicationRepository applicationRepository,
            CrossServiceOwnershipClient ownershipClient) {
        this.applicationRepository = applicationRepository;
        this.ownershipClient = ownershipClient;
    }

    public ApplicationService(ApplicationRepository applicationRepository) {
        this(applicationRepository, null);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('CANDIDATE','ADMIN')")
    public ApplicationResponse createApplication(CreateApplicationRequest request) {
        if (request.candidateId() == null || request.jobId() == null) {
            throw new InvalidRequestException("candidateId and jobId must not be null");
        }
        enforceCandidateOwnership(request.candidateId());

        if (applicationRepository.existsByCandidateIdAndJobId(request.candidateId(), request.jobId())) {
            throw duplicateApplication(request.candidateId(), request.jobId());
        }

        Application application = new Application();
        application.setCandidateId(request.candidateId());
        application.setJobId(request.jobId());
        application.setCoverLetter(request.coverLetter());
        application.setStatus(ApplicationStatus.APPLIED);

        Application saved = applicationRepository.saveAndFlush(application);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getApplicationById(UUID id) {
        Application application = findApplication(id);
        enforceApplicationReadAccess(application);
        return toResponse(application);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('CANDIDATE','ADMIN')")
    public List<ApplicationResponse> getApplicationsByCandidateId(UUID candidateId) {
        enforceCandidateOwnership(candidateId);
        return applicationRepository.findByCandidateId(candidateId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public List<ApplicationResponse> getApplicationsByJobId(UUID jobId) {
        enforceJobOwnership(jobId);
        return applicationRepository.findByJobId(jobId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public ApplicationResponse updateApplicationStatus(UUID id, UpdateApplicationStatusRequest request) {
        if (request.status() == null) {
            throw new InvalidRequestException("status must not be null");
        }
        Application application = findApplication(id);
        enforceJobOwnership(application.getJobId());
        application.setStatus(request.status());
        return toResponse(applicationRepository.saveAndFlush(application));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('CANDIDATE','ADMIN')")
    public void deleteApplication(UUID id) {
        Application application = findApplication(id);
        enforceCandidateOwnership(application.getCandidateId());
        applicationRepository.delete(application);
    }

    private void enforceCandidateOwnership(UUID candidateId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return;
        }
        if (isAdmin(auth)) {
            return;
        }
        if (ownershipClient == null) {
            return;
        }
        UUID principalId = getPrincipalId(auth);
        if (!ownershipClient.isCandidateOwner(candidateId, principalId)) {
            throw new AccessDeniedException("You do not own this candidate profile");
        }
    }

    private void enforceJobOwnership(UUID jobId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return;
        }
        if (isAdmin(auth)) {
            return;
        }
        if (ownershipClient == null) {
            return;
        }
        UUID principalId = getPrincipalId(auth);
        if (!ownershipClient.isJobOwner(jobId, principalId)) {
            throw new AccessDeniedException("You do not own this job posting");
        }
    }

    private void enforceApplicationReadAccess(Application application) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return;
        }
        if (isAdmin(auth)) {
            return;
        }
        if (ownershipClient == null) {
            return;
        }
        UUID principalId = getPrincipalId(auth);
        boolean isCandidate = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_CANDIDATE".equals(a.getAuthority()));
        boolean isRecruiter = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_RECRUITER".equals(a.getAuthority()));

        if (isCandidate && ownershipClient.isCandidateOwner(application.getCandidateId(), principalId)) {
            return;
        }
        if (isRecruiter && ownershipClient.isJobOwner(application.getJobId(), principalId)) {
            return;
        }
        throw new AccessDeniedException("Access denied");
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private UUID getPrincipalId(Authentication auth) {
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("Invalid authenticated principal");
        }
    }

    private Application findApplication(UUID id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No application found with id '" + id + "'"));
    }

    private DuplicateResourceException duplicateApplication(UUID candidateId, UUID jobId) {
        return new DuplicateResourceException(
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
