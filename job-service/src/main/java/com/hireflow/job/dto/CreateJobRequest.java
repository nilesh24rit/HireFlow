package com.hireflow.job.dto;

import java.util.List;
import java.util.UUID;

import com.hireflow.job.entity.EmploymentType;
import com.hireflow.job.entity.JobStatus;
import com.hireflow.job.validation.JobRangeValid;
import com.hireflow.job.validation.JobRanges;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@JobRangeValid
@Schema(description = "Payload used to create a job posting")
public record CreateJobRequest(
        @NotNull(message = "recruiterId must not be null")
        @Schema(description = "UUID of the recruiter who owns the job, as issued by the auth service",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID recruiterId,
        @NotBlank(message = "title must not be blank")
        @Size(max = 200, message = "title must be at most 200 characters")
        @Schema(description = "Job title", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Senior Java Developer")
        String title,
        @NotBlank(message = "description must not be blank")
        @Size(max = 10000, message = "description must be at most 10000 characters")
        @Schema(description = "Full job description", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Build and maintain Spring Boot services")
        String description,
        @Size(max = 200, message = "location must be at most 200 characters")
        @Schema(description = "Primary work location", example = "Berlin")
        String location,
        @NotNull(message = "employmentType must not be null")
        @Schema(description = "Employment type", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "FULL_TIME")
        EmploymentType employmentType,
        @PositiveOrZero(message = "experienceMin must not be negative")
        @Schema(description = "Minimum years of experience required", example = "3")
        Integer experienceMin,
        @PositiveOrZero(message = "experienceMax must not be negative")
        @Schema(description = "Maximum years of experience allowed", example = "6")
        Integer experienceMax,
        @PositiveOrZero(message = "salaryMin must not be negative")
        @Schema(description = "Lower bound of the salary range", example = "60000")
        Integer salaryMin,
        @PositiveOrZero(message = "salaryMax must not be negative")
        @Schema(description = "Upper bound of the salary range", example = "90000")
        Integer salaryMax,
        @Schema(description = "Initial job status; defaults to DRAFT when omitted", example = "DRAFT")
        JobStatus status,
        @Schema(description = "Skills required for the job")
        List<@NotBlank(message = "skill must not be blank") String> skills)
        implements JobRanges {
}
