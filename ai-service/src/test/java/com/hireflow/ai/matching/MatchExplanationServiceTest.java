package com.hireflow.ai.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.hireflow.ai.config.AiProperties;
import com.hireflow.ai.matching.api.CandidateMatchProfile;
import com.hireflow.ai.matching.api.DeterministicMatch;
import com.hireflow.ai.matching.api.JobMatchProfile;
import com.hireflow.ai.matching.explanation.MatchExplanationService;

class MatchExplanationServiceTest {

    @Test
    void preservesDeterministicResultWhenProviderIsNotConfigured() {
        AiProperties properties = new AiProperties("", "test-model", java.time.Duration.ofSeconds(1),
                100, 0, 1000, 1000);
        MatchExplanationService service = new MatchExplanationService(mock(), properties);
        var result = service.explain(
                new CandidateMatchProfile(UUID.randomUUID(), List.of("Java"), 3, List.of(), List.of()),
                new JobMatchProfile(UUID.randomUUID(), UUID.randomUUID(), List.of("Java"), List.of(),
                        null, null, List.of(), "Build APIs", "OPEN"),
                new DeterministicMatch(100, List.of("Java"), List.of(), List.of(),
                        "Documented experience is available", "Unavailable", List.of("education")));

        assertThat(result.text()).isNull();
        assertThat(result.status()).isEqualTo("UNAVAILABLE");
    }
}
