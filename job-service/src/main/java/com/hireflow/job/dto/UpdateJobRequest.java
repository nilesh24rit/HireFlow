package com.hireflow.job.dto;

import java.util.List;

import com.hireflow.job.entity.EmploymentType;
import com.hireflow.job.entity.JobStatus;
import com.hireflow.job.validation.JobRangeValid;
import com.hireflow.job.validation.JobRanges;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@JobRangeValid
@Schema(description = "Payload used to update a job posting; omitted fields are left unchanged")
public record UpdateJobRequest(
        @NotBlank(message = "title must not be blank")
        @Size(max = 200, message = "title must be at most 200 characters")
        @Schema(description = "Job title")
        String title,
        @NotBlank(message = "description must not be blank")
        @Size(max = 10000, message = "description must be at most 10000 characters")
        @Schema(description = "Full job description")
        String description,
        @Size(max = 200, message = "location must be at most 200 characters")
        @Schema(description = "Primary work location")
        String location,
        @Schema(description = "Employment type")
        EmploymentType employmentType,
        @PositiveOrZero(message = "experienceMin must not be negative")
        @Schema(description = "Minimum years of experience required")
        Integer experienceMin,
        @PositiveOrZero(message = "experienceMax must not be negative")
        @Schema(description = "Maximum years of experience allowed")
        Integer experienceMax,
        @PositiveOrZero(message = "salaryMin must not be negative")
        @Schema(description = "Lower bound of the salary range")
        Integer salaryMin,
        @PositiveOrZero(message = "salaryMax must not be negative")
        @Schema(description = "Upper bound of the salary range")
        Integer salaryMax,
        @Schema(description = "Job status")
        JobStatus status,
        @Schema(description = "Replaces the current skill set when supplied")
        List<@NotBlank(message = "skill must not be blank") String> skills)
        implements JobRanges {
}
