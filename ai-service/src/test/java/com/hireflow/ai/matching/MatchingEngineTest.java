package com.hireflow.ai.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hireflow.ai.matching.api.CandidateMatchProfile;
import com.hireflow.ai.matching.api.JobMatchProfile;
import com.hireflow.ai.matching.api.SkillNormalizer;
import com.hireflow.ai.matching.engine.MatchingEngine;

class MatchingEngineTest {

    private final MatchingEngine engine = new MatchingEngine();

    @Test
    void normalizesSkillsAndReportsMissingRequiredSkills() {
        var candidate = new CandidateMatchProfile(UUID.randomUUID(), List.of(" Java-Script ", "Spring Boot"),
                4, List.of(), List.of());
        var job = new JobMatchProfile(UUID.randomUUID(), UUID.randomUUID(), List.of("javascript", "Java", "Spring Boot"),
                List.of(), 3, 6, List.of(), "Build APIs", "OPEN");

        var result = engine.calculate(candidate, job);

        assertThat(result.matchedRequiredSkills()).containsExactly("javascript", "Spring Boot");
        assertThat(result.missingRequiredSkills()).containsExactly("Java");
        assertThat(result.score()).isBetween(0, 100);
    }

    @Test
    void normalizesOverOnlyEvaluableCriteria() {
        var candidate = new CandidateMatchProfile(UUID.randomUUID(), List.of("Java"), null, List.of(), List.of());
        var job = new JobMatchProfile(UUID.randomUUID(), UUID.randomUUID(), List.of("Java"), List.of(), 3, null,
                List.of(), "Build APIs", "OPEN");

        assertThat(engine.calculate(candidate, job).score()).isEqualTo(100);
    }

    @Test
    void normalizationIsStable() {
        assertThat(SkillNormalizer.normalize(" C++ / .NET ")).isEqualTo("c net");
    }
}
