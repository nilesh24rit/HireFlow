package com.hireflow.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireflow.candidate.client.AiResumeClient;
import com.hireflow.candidate.config.ResumeProcessingProperties;
import com.hireflow.candidate.dto.ResumeConfirmationRequest;
import com.hireflow.candidate.dto.ResumeData;
import com.hireflow.candidate.entity.Candidate;
import com.hireflow.candidate.entity.ResumeEnrichment;
import com.hireflow.candidate.entity.ResumeEnrichmentStatus;
import com.hireflow.candidate.repository.CandidateRepository;
import com.hireflow.candidate.repository.ResumeEnrichmentRepository;

@ExtendWith(MockitoExtension.class)
class ResumeEnrichmentServiceTest {

    @Mock
    private CandidateRepository candidateRepository;

    @Mock
    private ResumeEnrichmentRepository enrichmentRepository;

    @Mock
    private CandidateService candidateService;

    @Mock
    private AiResumeClient aiResumeClient;

    private ResumeEnrichmentService service;
    private UUID userId;
    private UUID candidateId;

    @BeforeEach
    void setUp() {
        ResumeProcessingProperties properties = new ResumeProcessingProperties();
        properties.setProposalTtl(Duration.ofHours(24));
        service = new ResumeEnrichmentService(candidateRepository, enrichmentRepository,
                candidateService, aiResumeClient, properties, new ObjectMapper());
        userId = UUID.randomUUID();
        candidateId = UUID.randomUUID();
    }

    @Test
    void createsPendingProposalWithoutChangingCandidate() {
        Candidate candidate = new Candidate();
        ReflectionTestUtils.setField(candidate, "id", candidateId);
        ResumeData data = data();
        when(candidateRepository.findByUserId(userId)).thenReturn(Optional.of(candidate));
        when(aiResumeClient.process(any(), any())).thenReturn(data);
        when(enrichmentRepository.save(any(ResumeEnrichment.class))).thenAnswer(invocation -> {
            ResumeEnrichment enrichment = invocation.getArgument(0);
            ReflectionTestUtils.setField(enrichment, "id", UUID.randomUUID());
            return enrichment;
        });

        var response = service.process(userId,
                new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[] { 1 }),
                "Bearer token");

        assertThat(response.status()).isEqualTo(ResumeEnrichmentStatus.PENDING);
        assertThat(response.proposedData().skills()).containsExactly("Java");
        verify(candidateService, org.mockito.Mockito.never()).applyResumeEnrichment(any(), any());
    }

    @Test
    void confirmationAppliesOnlyAfterExplicitRequest() {
        UUID enrichmentId = UUID.randomUUID();
        ResumeEnrichment enrichment = new ResumeEnrichment();
        ReflectionTestUtils.setField(enrichment, "id", enrichmentId);
        enrichment.setCandidateId(candidateId);
        enrichment.setProcessingId(UUID.randomUUID());
        enrichment.setProposedData(new ObjectMapper().valueToTree(data()).toString());
        enrichment.setStatus(ResumeEnrichmentStatus.PENDING);
        enrichment.setCreatedAt(Instant.now());
        enrichment.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        when(candidateRepository.findByUserId(userId)).thenReturn(Optional.of(candidate(candidateId)));
        when(enrichmentRepository.findByIdAndCandidateId(enrichmentId, candidateId))
                .thenReturn(Optional.of(enrichment));
        when(enrichmentRepository.saveAndFlush(enrichment)).thenReturn(enrichment);

        var request = new ResumeConfirmationRequest("Engineer", "Experienced developer",
                5, null, null, List.of("Java"));
        var response = service.confirm(userId, enrichmentId, request);

        verify(candidateService).applyResumeEnrichment(candidateId, request);
        assertThat(response.status()).isEqualTo(ResumeEnrichmentStatus.CONFIRMED);
    }

    private ResumeData data() {
        return new ResumeData(UUID.randomUUID(), "Jane", null, null, List.of("Java"),
                List.of(), List.of(), List.of(), List.of(), "Developer", List.of());
    }

    private Candidate candidate(UUID id) {
        Candidate candidate = new Candidate();
        ReflectionTestUtils.setField(candidate, "id", id);
        candidate.setUserId(userId);
        return candidate;
    }
}
