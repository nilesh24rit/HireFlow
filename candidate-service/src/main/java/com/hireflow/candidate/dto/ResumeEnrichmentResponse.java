package com.hireflow.candidate.dto;

import java.time.Instant;
import java.util.UUID;

import com.hireflow.candidate.entity.ResumeEnrichmentStatus;

public record ResumeEnrichmentResponse(
        UUID id,
        UUID processingId,
        ResumeEnrichmentStatus status,
        ResumeData proposedData,
        Instant createdAt,
        Instant expiresAt,
        Instant confirmedAt) {
}
