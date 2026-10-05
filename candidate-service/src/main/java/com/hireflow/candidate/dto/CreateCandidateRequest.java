package com.hireflow.candidate.dto;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.hibernate.validator.constraints.URL;

@Schema(description = "Payload used to create a candidate profile")
public record CreateCandidateRequest(
        @NotNull(message = "userId must not be null")
        @Schema(description = "UUID of the owning user, as issued by the auth service",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
        UUID userId,
        @Size(max = 200, message = "headline must be at most 200 characters")
        @Schema(description = "Short professional headline", example = "Senior Java Developer")
        String headline,
        @Size(max = 5000, message = "summary must be at most 5000 characters")
        @Schema(description = "Free-form professional summary")
        String summary,
        @Size(max = 200, message = "location must be at most 200 characters")
        @Schema(description = "Primary work location", example = "Berlin")
        String location,
        @Min(value = 0, message = "yearsOfExperience must not be negative")
        @Schema(description = "Years of professional experience", example = "5")
        Integer yearsOfExperience,
        @Size(max = 200, message = "currentCompany must be at most 200 characters")
        @Schema(description = "Current employer", example = "Acme")
        String currentCompany,
        @Size(max = 200, message = "currentJobTitle must be at most 200 characters")
        @Schema(description = "Current job title", example = "Software Engineer")
        String currentJobTitle,
        @URL(regexp = "^https?://.+", message = "resumeUrl must be a valid URL")
        @Size(max = 500, message = "resumeUrl must be at most 500 characters")
        @Schema(description = "URL of the uploaded resume")
        String resumeUrl,
        @URL(regexp = "^https?://.+", message = "linkedinUrl must be a valid URL")
        @Size(max = 500, message = "linkedinUrl must be at most 500 characters")
        @Schema(description = "LinkedIn profile URL")
        String linkedinUrl,
        @URL(regexp = "^https?://.+", message = "githubUrl must be a valid URL")
        @Size(max = 500, message = "githubUrl must be at most 500 characters")
        @Schema(description = "GitHub profile URL")
        String githubUrl,
        @Schema(description = "Skills of the candidate")
        List<@NotBlank(message = "skill must not be blank")
        @Size(max = 100, message = "skill must be at most 100 characters") String> skills) {
}
