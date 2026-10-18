package com.hireflow.contracts.events;

import java.time.Instant;
import java.util.UUID;

public record ApplicationEvent(
        UUID eventId,
        ApplicationEventType eventType,
        int eventVersion,
        Instant occurredAt,
        UUID aggregateId,
        UUID correlationId,
        ApplicationEventPayload payload) {

    public static final int CURRENT_VERSION = 1;

    public void validate() {
        if (eventId == null || eventType == null || occurredAt == null
                || aggregateId == null || correlationId == null || payload == null) {
            throw new IllegalArgumentException("Application event envelope is incomplete");
        }
        if (eventVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported application event version: " + eventVersion);
        }
        if (payload.applicationId() == null || payload.candidateId() == null || payload.jobId() == null
                || payload.currentStatus() == null || !aggregateId.equals(payload.applicationId())) {
            throw new IllegalArgumentException("Application event payload is incomplete");
        }
        if (eventType == ApplicationEventType.APPLICATION_STATUS_CHANGED
                && payload.previousStatus() == null) {
            throw new IllegalArgumentException("Status change event requires previousStatus");
        }
        if (eventType == ApplicationEventType.APPLICATION_WITHDRAWN
                && !"WITHDRAWN".equals(payload.currentStatus())) {
            throw new IllegalArgumentException("Withdrawn event requires WITHDRAWN status");
        }
    }
}
