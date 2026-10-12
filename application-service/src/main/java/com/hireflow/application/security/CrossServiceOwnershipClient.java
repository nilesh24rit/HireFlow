package com.hireflow.application.security;

import java.util.UUID;

/**
 * Boundary interface for cross-service resource ownership verification.
 *
 * <p>Enforces authorization across service boundaries without introducing direct
 * database coupling or JPA relationships between Application Service, Candidate
 * Service and Job Service.</p>
 */
public interface CrossServiceOwnershipClient {

    /**
     * Verifies whether the specified candidate profile belongs to the given user id.
     *
     * @param candidateId candidate profile UUID
     * @param userId      authenticated user UUID
     * @return true if the candidate profile is owned by the user, false otherwise
     */
    boolean isCandidateOwner(UUID candidateId, UUID userId);

    /**
     * Verifies whether the specified job posting is owned by the given recruiter user id.
     *
     * @param jobId       job UUID
     * @param recruiterId authenticated recruiter user UUID
     * @return true if the job is owned by the recruiter, false otherwise
     */
    boolean isJobOwner(UUID jobId, UUID recruiterId);
}
