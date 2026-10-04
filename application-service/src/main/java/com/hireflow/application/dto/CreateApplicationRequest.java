package com.hireflow.application.dto;

import java.util.UUID;

public record CreateApplicationRequest(
        UUID candidateId,
        UUID jobId,
        String coverLetter) {
}
