package com.hireflow.application.dto;

import java.time.Instant;
import java.util.UUID;

import com.hireflow.application.entity.ApplicationStatus;

public record ApplicationResponse(
        UUID id,
        UUID candidateId,
        UUID jobId,
        ApplicationStatus status,
        String coverLetter,
        Instant appliedAt,
        Instant updatedAt) {
}
