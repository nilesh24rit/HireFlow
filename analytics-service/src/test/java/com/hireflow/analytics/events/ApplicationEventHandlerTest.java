package com.hireflow.analytics.events;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hireflow.contracts.events.ApplicationEvent;
import com.hireflow.contracts.events.ApplicationEventPayload;
import com.hireflow.contracts.events.ApplicationEventType;

class ApplicationEventHandlerTest {

    @Test
    void duplicateEventDoesNotFailAnalyticsProcessing() {
        UUID applicationId = UUID.randomUUID();
        ApplicationEvent event = new ApplicationEvent(UUID.randomUUID(),
                ApplicationEventType.APPLICATION_STATUS_CHANGED, 1, Instant.now(), applicationId,
                UUID.randomUUID(), new ApplicationEventPayload(applicationId, UUID.randomUUID(),
                        UUID.randomUUID(), "APPLIED", "UNDER_REVIEW"));
        ApplicationEventHandler handler = new ApplicationEventHandler();

        assertDoesNotThrow(() -> handler.handle(event));
        assertDoesNotThrow(() -> handler.handle(event));
    }
}
