package com.hireflow.candidate.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Candidate profile returned by the candidate service")
public record CandidateResponse(
        @Schema(description = "Candidate identifier", example = "3f1a8f9e-0d2c-4a6b-9f3e-1b2c3d4e5f60")
        UUID id,
        @Schema(description = "UUID of the owning user", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
        UUID userId,
        @Schema(description = "Short professional headline", example = "Senior Java Developer")
        String headline,
        @Schema(description = "Free-form professional summary")
        String summary,
        @Schema(description = "Primary work location", example = "Berlin")
        String location,
        @Schema(description = "Years of professional experience", example = "5")
        Integer yearsOfExperience,
        @Schema(description = "Current employer", example = "Acme")
        String currentCompany,
        @Schema(description = "Current job title", example = "Software Engineer")
        String currentJobTitle,
        @Schema(description = "URL of the uploaded resume")
        String resumeUrl,
        @Schema(description = "LinkedIn profile URL")
        String linkedinUrl,
        @Schema(description = "GitHub profile URL")
        String githubUrl,
        @Schema(description = "Skills of the candidate")
        List<String> skills,
        @Schema(description = "Creation timestamp in UTC")
        Instant createdAt,
        @Schema(description = "Last update timestamp in UTC")
        Instant updatedAt) {
}
