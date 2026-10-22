package com.hireflow.candidate.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireflow.candidate.client.AiResumeClient;
import com.hireflow.candidate.config.ResumeProcessingProperties;
import com.hireflow.candidate.dto.ResumeConfirmationRequest;
import com.hireflow.candidate.dto.ResumeData;
import com.hireflow.candidate.dto.ResumeEnrichmentResponse;
import com.hireflow.candidate.entity.Candidate;
import com.hireflow.candidate.entity.ResumeEnrichment;
import com.hireflow.candidate.entity.ResumeEnrichmentStatus;
import com.hireflow.candidate.exception.InvalidRequestException;
import com.hireflow.candidate.exception.ResourceNotFoundException;
import com.hireflow.candidate.repository.CandidateRepository;
import com.hireflow.candidate.repository.ResumeEnrichmentRepository;

@Service
public class ResumeEnrichmentService {

    private final CandidateRepository candidateRepository;
    private final ResumeEnrichmentRepository enrichmentRepository;
    private final CandidateService candidateService;
    private final AiResumeClient aiResumeClient;
    private final ResumeProcessingProperties properties;
    private final ObjectMapper objectMapper;

    public ResumeEnrichmentService(CandidateRepository candidateRepository,
            ResumeEnrichmentRepository enrichmentRepository, CandidateService candidateService,
            AiResumeClient aiResumeClient, ResumeProcessingProperties properties,
            ObjectMapper objectMapper) {
        this.candidateRepository = candidateRepository;
        this.enrichmentRepository = enrichmentRepository;
        this.candidateService = candidateService;
        this.aiResumeClient = aiResumeClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ResumeEnrichmentResponse process(UUID userId, MultipartFile file, String authorization) {
        Candidate candidate = candidateRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No candidate found for the authenticated user"));
        validateFile(file);
        ResumeData data = aiResumeClient.process(file, authorization);
        if (data.processingId() == null) {
            throw new InvalidRequestException("Resume processing returned no processing id");
        }
        ResumeEnrichment enrichment = new ResumeEnrichment();
        enrichment.setCandidateId(candidate.getId());
        enrichment.setProcessingId(data.processingId());
        enrichment.setProposedData(write(data));
        enrichment.setStatus(ResumeEnrichmentStatus.PENDING);
        enrichment.setCreatedAt(Instant.now());
        enrichment.setExpiresAt(Instant.now().plus(properties.getProposalTtl()));
        return toResponse(enrichmentRepository.save(enrichment));
    }

    @Transactional
    public ResumeEnrichmentResponse get(UUID userId, UUID id) {
        ResumeEnrichment enrichment = findOwned(userId, id);
        expireIfNeeded(enrichment);
        return toResponse(enrichment);
    }

    @Transactional
    public ResumeEnrichmentResponse confirm(UUID userId, UUID id, ResumeConfirmationRequest request) {
        ResumeEnrichment enrichment = findOwned(userId, id);
        ensurePending(enrichment);
        candidateService.applyResumeEnrichment(enrichment.getCandidateId(), request);
        enrichment.setStatus(ResumeEnrichmentStatus.CONFIRMED);
        enrichment.setConfirmedAt(Instant.now());
        try {
            return toResponse(enrichmentRepository.saveAndFlush(enrichment));
        } catch (OptimisticLockingFailureException ex) {
            throw new InvalidRequestException("This enrichment has already been processed");
        }
    }

    @Transactional
    public ResumeEnrichmentResponse reject(UUID userId, UUID id) {
        ResumeEnrichment enrichment = findOwned(userId, id);
        ensurePending(enrichment);
        enrichment.setStatus(ResumeEnrichmentStatus.REJECTED);
        return toResponse(enrichmentRepository.saveAndFlush(enrichment));
    }

    private ResumeEnrichment findOwned(UUID userId, UUID id) {
        Candidate candidate = candidateRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No candidate found for the authenticated user"));
        return enrichmentRepository.findByIdAndCandidateId(id, candidate.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume enrichment was not found"));
    }

    private void ensurePending(ResumeEnrichment enrichment) {
        expireIfNeeded(enrichment);
        if (enrichment.getStatus() != ResumeEnrichmentStatus.PENDING) {
            throw new InvalidRequestException("Resume enrichment is no longer pending");
        }
    }

    private void expireIfNeeded(ResumeEnrichment enrichment) {
        if (enrichment.getStatus() == ResumeEnrichmentStatus.PENDING
                && !enrichment.getExpiresAt().isAfter(Instant.now())) {
            enrichment.setStatus(ResumeEnrichmentStatus.EXPIRED);
            enrichmentRepository.save(enrichment);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Resume file is required");
        }
        if (file.getSize() > properties.getMaxResumeBytes()) {
            throw new InvalidRequestException("Resume file exceeds the configured size limit");
        }
        String name = file.getOriginalFilename();
        if (name == null || !(name.toLowerCase().endsWith(".pdf")
                || name.toLowerCase().endsWith(".docx"))) {
            throw new InvalidRequestException("Only PDF and DOCX resumes are supported");
        }
    }

    private String write(ResumeData data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            throw new InvalidRequestException("Resume proposal could not be stored");
        }
    }

    private ResumeEnrichmentResponse toResponse(ResumeEnrichment enrichment) {
        try {
            return new ResumeEnrichmentResponse(
                    enrichment.getId(), enrichment.getProcessingId(), enrichment.getStatus(),
                    objectMapper.readValue(enrichment.getProposedData(), ResumeData.class),
                    enrichment.getCreatedAt(), enrichment.getExpiresAt(), enrichment.getConfirmedAt());
        } catch (JsonProcessingException ex) {
            throw new InvalidRequestException("Resume proposal is unreadable");
        }
    }
}
