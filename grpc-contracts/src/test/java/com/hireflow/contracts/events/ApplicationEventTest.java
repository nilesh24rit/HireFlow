package com.hireflow.contracts.events;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ApplicationEventTest {

    @Test
    void validatesVersionOneEnvelopeAndPayload() {
        UUID applicationId = UUID.randomUUID();
        ApplicationEvent event = new ApplicationEvent(
                UUID.randomUUID(), ApplicationEventType.APPLICATION_SUBMITTED, 1, Instant.now(),
                applicationId, UUID.randomUUID(),
                new ApplicationEventPayload(applicationId, UUID.randomUUID(), UUID.randomUUID(),
                        null, "APPLIED"));

        assertDoesNotThrow(event::validate);
    }

    @Test
    void rejectsUnknownVersion() {
        ApplicationEvent event = new ApplicationEvent(
                UUID.randomUUID(), ApplicationEventType.APPLICATION_SUBMITTED, 2, Instant.now(),
                UUID.randomUUID(), UUID.randomUUID(),
                new ApplicationEventPayload(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        null, "APPLIED"));

        assertThrows(IllegalArgumentException.class, event::validate);
    }
}
