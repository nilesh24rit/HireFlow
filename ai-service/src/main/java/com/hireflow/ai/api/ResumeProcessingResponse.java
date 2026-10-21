package com.hireflow.ai.api;

import java.util.List;
import java.util.UUID;

public record ResumeProcessingResponse(
        UUID processingId,
        String fullName,
        String email,
        String phone,
        List<String> skills,
        List<String> education,
        List<String> experience,
        List<String> projects,
        List<String> certifications,
        String summary,
        List<String> warnings) {

    public ResumeProcessingResponse {
        skills = skills == null ? List.of() : List.copyOf(skills);
        education = education == null ? List.of() : List.copyOf(education);
        experience = experience == null ? List.of() : List.copyOf(experience);
        projects = projects == null ? List.of() : List.copyOf(projects);
        certifications = certifications == null ? List.of() : List.copyOf(certifications);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
