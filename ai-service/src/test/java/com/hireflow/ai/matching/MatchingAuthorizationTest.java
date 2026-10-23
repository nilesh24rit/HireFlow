package com.hireflow.ai.matching;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hireflow.ai.matching.api.JobMatchProfile;
import com.hireflow.ai.matching.client.MatchingDataClient;
import com.hireflow.ai.matching.engine.MatchingEngine;
import com.hireflow.ai.matching.explanation.MatchExplanationService;
import com.hireflow.ai.matching.service.MatchingAuthorizationException;
import com.hireflow.ai.matching.service.MatchingService;
import com.hireflow.ai.security.AuthenticatedUser;

class MatchingAuthorizationTest {

    @Test
    void recruiterCannotMatchCandidateOnAnotherRecruitersJob() {
        MatchingDataClient data = mock(MatchingDataClient.class);
        MatchingEngine engine = new MatchingEngine();
        MatchExplanationService explanation = mock(MatchExplanationService.class);
        MatchingService service = new MatchingService(data, engine, explanation);
        UUID jobId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        when(data.getJob(jobId)).thenReturn(new JobMatchProfile(jobId, UUID.randomUUID(),
                List.of("Java"), List.of(), null, null, List.of(), "Build APIs", "OPEN"));

        assertThatThrownBy(() -> service.matchForRecruiter(jobId, candidateId,
                new AuthenticatedUser(UUID.randomUUID(), "RECRUITER")))
                .isInstanceOf(MatchingAuthorizationException.class);
    }
}
