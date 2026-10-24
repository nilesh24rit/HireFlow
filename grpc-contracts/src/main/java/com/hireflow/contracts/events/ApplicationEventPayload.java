package com.hireflow.contracts.events;

import java.util.UUID;

public record ApplicationEventPayload(
        UUID applicationId,
        UUID candidateId,
        UUID jobId,
        String previousStatus,
        String currentStatus,
        UUID recruiterUserId,
        String jobTitle) {

    public ApplicationEventPayload(UUID applicationId, UUID candidateId, UUID jobId,
            String previousStatus, String currentStatus) {
        this(applicationId, candidateId, jobId, previousStatus, currentStatus, null, null);
    }
}
