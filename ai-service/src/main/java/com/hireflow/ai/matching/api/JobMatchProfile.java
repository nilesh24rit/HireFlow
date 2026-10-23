package com.hireflow.ai.matching.api;

import java.util.List;
import java.util.UUID;

public record JobMatchProfile(
        UUID jobId,
        List<String> requiredSkills,
        List<String> preferredSkills,
        Integer minimumExperience,
        Integer maximumExperience,
        List<String> educationRequirements,
        String description,
        String status) {

    public JobMatchProfile {
        requiredSkills = requiredSkills == null ? List.of() : List.copyOf(requiredSkills);
        preferredSkills = preferredSkills == null ? List.of() : List.copyOf(preferredSkills);
        educationRequirements = educationRequirements == null ? List.of() : List.copyOf(educationRequirements);
    }
}
