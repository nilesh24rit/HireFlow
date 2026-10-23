package com.hireflow.ai.matching.api;

import java.util.List;
import java.util.UUID;

public record CandidateMatchProfile(
        UUID candidateId,
        List<String> skills,
        Integer yearsOfExperience,
        List<String> education,
        List<String> projects) {

    public CandidateMatchProfile {
        skills = skills == null ? List.of() : List.copyOf(skills);
        education = education == null ? List.of() : List.copyOf(education);
        projects = projects == null ? List.of() : List.copyOf(projects);
    }
}
