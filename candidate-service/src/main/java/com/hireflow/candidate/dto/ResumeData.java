package com.hireflow.candidate.dto;

import java.util.List;
import java.util.UUID;

public record ResumeData(
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

    public ResumeData {
        skills = immutable(skills);
        education = immutable(education);
        experience = immutable(experience);
        projects = immutable(projects);
        certifications = immutable(certifications);
        warnings = immutable(warnings);
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
