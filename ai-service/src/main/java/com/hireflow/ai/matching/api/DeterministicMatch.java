package com.hireflow.ai.matching.api;

import java.util.List;

public record DeterministicMatch(
        int score,
        List<String> matchedRequiredSkills,
        List<String> missingRequiredSkills,
        List<String> matchedPreferredSkills,
        String experienceAssessment,
        String educationAssessment,
        List<String> unavailableCriteria) {
}
