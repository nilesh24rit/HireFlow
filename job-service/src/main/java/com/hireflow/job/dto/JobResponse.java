package com.hireflow.job.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.hireflow.job.entity.EmploymentType;
import com.hireflow.job.entity.JobStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Job posting returned by the job service")
public record JobResponse(
        @Schema(description = "Job identifier", example = "3f1a8f9e-0d2c-4a6b-9f3e-1b2c3d4e5f60")
        UUID id,
        @Schema(description = "UUID of the owning recruiter", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
        UUID recruiterId,
        @Schema(description = "Job title", example = "Senior Java Developer")
        String title,
        @Schema(description = "Full job description")
        String description,
        @Schema(description = "Primary work location", example = "Berlin")
        String location,
        @Schema(description = "Employment type", example = "FULL_TIME")
        EmploymentType employmentType,
        @Schema(description = "Minimum years of experience required", example = "3")
        Integer experienceMin,
        @Schema(description = "Maximum years of experience allowed", example = "6")
        Integer experienceMax,
        @Schema(description = "Lower bound of the salary range", example = "60000")
        Integer salaryMin,
        @Schema(description = "Upper bound of the salary range", example = "90000")
        Integer salaryMax,
        @Schema(description = "Current job status", example = "OPEN")
        JobStatus status,
        @Schema(description = "Skills required for the job")
        List<String> skills,
        @Schema(description = "Creation timestamp in UTC")
        Instant createdAt,
        @Schema(description = "Last update timestamp in UTC")
        Instant updatedAt) {
}
