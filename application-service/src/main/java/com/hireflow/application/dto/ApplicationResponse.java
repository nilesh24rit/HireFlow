package com.hireflow.application.dto;

import java.time.Instant;
import java.util.UUID;

import com.hireflow.application.entity.ApplicationStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Job application returned by the application service")
public record ApplicationResponse(
        @Schema(description = "Application identifier", example = "3f1a8f9e-0d2c-4a6b-9f3e-1b2c3d4e5f60")
        UUID id,
        @Schema(description = "UUID of the candidate who applied",
                example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
        UUID candidateId,
        @Schema(description = "UUID of the job applied to", example = "3f1a8f9e-0d2c-4a6b-9f3e-1b2c3d4e5f60")
        UUID jobId,
        @Schema(description = "Current application status", example = "APPLIED")
        ApplicationStatus status,
        @Schema(description = "Cover letter submitted with the application")
        String coverLetter,
        @Schema(description = "Timestamp in UTC when the application was submitted")
        Instant appliedAt,
        @Schema(description = "Last update timestamp in UTC")
        Instant updatedAt) {
}
