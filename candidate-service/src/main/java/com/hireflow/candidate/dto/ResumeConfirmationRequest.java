package com.hireflow.candidate.dto;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ResumeConfirmationRequest(
        @Size(max = 200) String headline,
        @Size(max = 5000) String summary,
        @Min(0) Integer yearsOfExperience,
        @Size(max = 200) String currentCompany,
        @Size(max = 200) String currentJobTitle,
        List<@Size(max = 100) String> skills) {
}
