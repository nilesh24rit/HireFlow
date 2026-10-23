package com.hireflow.ai.matching.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Informational compatibility estimate; not a hiring decision or hiring probability")
public record MatchResponse(
        UUID candidateId,
        UUID jobId,
        int matchScore,
        List<String> matchedRequiredSkills,
        List<String> missingRequiredSkills,
        List<String> matchedPreferredSkills,
        String experienceAssessment,
        String educationAssessment,
        List<String> unavailableCriteria,
        String explanation,
        String explanationStatus,
        Instant generatedAt) {
}
