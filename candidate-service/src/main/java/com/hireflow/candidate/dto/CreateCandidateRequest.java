package com.hireflow.candidate.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.hibernate.validator.constraints.URL;

public record CreateCandidateRequest(
        @NotNull(message = "userId must not be null")
        UUID userId,
        @Size(max = 200, message = "headline must be at most 200 characters")
        String headline,
        @Size(max = 5000, message = "summary must be at most 5000 characters")
        String summary,
        @Size(max = 200, message = "location must be at most 200 characters")
        String location,
        @Min(value = 0, message = "yearsOfExperience must not be negative")
        Integer yearsOfExperience,
        @Size(max = 200, message = "currentCompany must be at most 200 characters")
        String currentCompany,
        @Size(max = 200, message = "currentJobTitle must be at most 200 characters")
        String currentJobTitle,
        @URL(regexp = "^https?://.+", message = "resumeUrl must be a valid URL")
        String resumeUrl,
        @URL(regexp = "^https?://.+", message = "linkedinUrl must be a valid URL")
        String linkedinUrl,
        @URL(regexp = "^https?://.+", message = "githubUrl must be a valid URL")
        String githubUrl,
        List<@NotBlank(message = "skill must not be blank") String> skills) {
}
