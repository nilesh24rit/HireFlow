package com.hireflow.ai.matching.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.hireflow.ai.matching.api.CandidateMatchProfile;
import com.hireflow.ai.matching.api.DeterministicMatch;
import com.hireflow.ai.matching.api.JobMatchProfile;
import com.hireflow.ai.matching.api.MatchResponse;
import com.hireflow.ai.matching.client.MatchingDataClient;
import com.hireflow.ai.matching.explanation.MatchExplanationService;
import com.hireflow.ai.matching.explanation.MatchExplanationService.ExplanationResult;
import com.hireflow.ai.security.AuthenticatedUser;

@Service
public class MatchingService {

    private final MatchingDataClient dataClient;
    private final com.hireflow.ai.matching.engine.MatchingEngine engine;
    private final MatchExplanationService explanationService;

    public MatchingService(MatchingDataClient dataClient,
            com.hireflow.ai.matching.engine.MatchingEngine engine,
            MatchExplanationService explanationService) {
        this.dataClient = dataClient;
        this.engine = engine;
        this.explanationService = explanationService;
    }

    public MatchResponse matchForCandidate(UUID jobId, AuthenticatedUser user) {
        requireRole(user, "CANDIDATE", "ADMIN");
        CandidateMatchProfile candidate = dataClient.getCandidateByUserId(user.userId());
        JobMatchProfile job = dataClient.getJob(jobId);
        requireOpen(job);
        return calculate(candidate, job);
    }

    public MatchResponse matchForRecruiter(UUID jobId, UUID candidateId, AuthenticatedUser user) {
        requireRole(user, "RECRUITER", "ADMIN");
        JobMatchProfile job = dataClient.getJob(jobId);
        if (!"ADMIN".equals(user.role()) && !jobOwner(job, user)) {
            throw new MatchingAuthorizationException("You do not own this job");
        }
        if (!dataClient.isApplicant(jobId, candidateId)) {
            throw new MatchingAuthorizationException("Candidate is not visible for this job");
        }
        return calculate(dataClient.getCandidateById(candidateId), job);
    }

    private MatchResponse calculate(CandidateMatchProfile candidate, JobMatchProfile job) {
        DeterministicMatch result = engine.calculate(candidate, job);
        ExplanationResult explanation = explanationService.explain(candidate, job, result);
        return new MatchResponse(candidate.candidateId(), job.jobId(), result.score(),
                result.matchedRequiredSkills(), result.missingRequiredSkills(),
                result.matchedPreferredSkills(), result.experienceAssessment(),
                result.educationAssessment(), result.unavailableCriteria(),
                explanation.text(), explanation.status(), Instant.now());
    }

    private boolean jobOwner(JobMatchProfile job, AuthenticatedUser user) {
        return user.userId().equals(job.recruiterId());
    }

    private void requireOpen(JobMatchProfile job) {
        if (!"OPEN".equalsIgnoreCase(job.status())) {
            throw new MatchingAuthorizationException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Job is not open or accessible");
        }
    }

    private void requireRole(AuthenticatedUser user, String... roles) {
        for (String role : roles) {
            if (role.equalsIgnoreCase(user.role())) {
                return;
            }
        }
        throw new MatchingAuthorizationException("Role is not permitted to request this match");
    }
}
