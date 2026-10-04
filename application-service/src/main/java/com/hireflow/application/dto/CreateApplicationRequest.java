package com.hireflow.application.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload used to create a job application")
public record CreateApplicationRequest(
        @NotNull(message = "candidateId must not be null")
        @Schema(description = "UUID of the candidate applying, as issued by the candidate service",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
        UUID candidateId,
        @NotNull(message = "jobId must not be null")
        @Schema(description = "UUID of the job being applied to, as issued by the job service",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "3f1a8f9e-0d2c-4a6b-9f3e-1b2c3d4e5f60")
        UUID jobId,
        @Size(max = 5000, message = "coverLetter must be at most 5000 characters")
        @Schema(description = "Optional cover letter accompanying the application")
        String coverLetter) {
}
