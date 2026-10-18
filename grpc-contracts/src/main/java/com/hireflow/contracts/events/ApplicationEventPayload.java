package com.hireflow.contracts.events;

import java.util.UUID;

public record ApplicationEventPayload(
        UUID applicationId,
        UUID candidateId,
        UUID jobId,
        String previousStatus,
        String currentStatus) {
}
