package com.hireflow.candidate.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CandidateResponse(
        UUID id,
        UUID userId,
        String headline,
        String summary,
        String location,
        Integer yearsOfExperience,
        String currentCompany,
        String currentJobTitle,
        String resumeUrl,
        String linkedinUrl,
        String githubUrl,
        List<String> skills,
        Instant createdAt,
        Instant updatedAt) {
}
