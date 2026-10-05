package com.hireflow.candidate.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.hibernate.validator.constraints.URL;

@Schema(description = "Payload used to update a candidate profile; omitted or null fields are left unchanged")
public record UpdateCandidateRequest(
        @Size(max = 200, message = "headline must be at most 200 characters")
        @Schema(description = "New professional headline")
        String headline,
        @Size(max = 5000, message = "summary must be at most 5000 characters")
        @Schema(description = "New professional summary")
        String summary,
        @Size(max = 200, message = "location must be at most 200 characters")
        @Schema(description = "New primary work location")
        String location,
        @Min(value = 0, message = "yearsOfExperience must not be negative")
        @Schema(description = "New years of professional experience")
        Integer yearsOfExperience,
        @Size(max = 200, message = "currentCompany must be at most 200 characters")
        @Schema(description = "New current employer")
        String currentCompany,
        @Size(max = 200, message = "currentJobTitle must be at most 200 characters")
        @Schema(description = "New current job title")
        String currentJobTitle,
        @URL(regexp = "^https?://.+", message = "resumeUrl must be a valid URL")
        @Size(max = 500, message = "resumeUrl must be at most 500 characters")
        @Schema(description = "New resume URL")
        String resumeUrl,
        @URL(regexp = "^https?://.+", message = "linkedinUrl must be a valid URL")
        @Size(max = 500, message = "linkedinUrl must be at most 500 characters")
        @Schema(description = "New LinkedIn profile URL")
        String linkedinUrl,
        @URL(regexp = "^https?://.+", message = "githubUrl must be a valid URL")
        @Size(max = 500, message = "githubUrl must be at most 500 characters")
        @Schema(description = "New GitHub profile URL")
        String githubUrl,
        @Schema(description = "Replaces the current skill set when supplied")
        List<@NotBlank(message = "skill must not be blank")
        @Size(max = 100, message = "skill must be at most 100 characters") String> skills) {
}
