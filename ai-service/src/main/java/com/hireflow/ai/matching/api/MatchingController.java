package com.hireflow.ai.matching.api;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hireflow.ai.matching.service.MatchingService;
import com.hireflow.ai.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/ai/matches")
@Tag(name = "Candidate-job matching")
@SecurityRequirement(name = "bearerAuth")
public class MatchingController {

    private final MatchingService matchingService;

    public MatchingController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @GetMapping("/me/jobs/{jobId}")
    @Operation(summary = "Compare the authenticated candidate with an open job",
            description = "Returns an informational compatibility estimate, not a hiring decision.")
    public MatchResponse matchCurrentCandidate(@PathVariable UUID jobId) {
        return matchingService.matchForCandidate(jobId, currentUser());
    }

    @GetMapping("/jobs/{jobId}/candidates/{candidateId}")
    @Operation(summary = "Compare an applicant with a recruiter-owned job",
            description = "Only applicants to the recruiter-owned job are visible.")
    public MatchResponse matchApplicant(@PathVariable UUID jobId, @PathVariable UUID candidateId) {
        return matchingService.matchForRecruiter(jobId, candidateId, currentUser());
    }

    private AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new IllegalStateException("Authenticated HireFlow identity is missing");
        }
        return user;
    }
}
