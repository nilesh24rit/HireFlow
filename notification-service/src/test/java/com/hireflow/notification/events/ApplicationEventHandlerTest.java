package com.hireflow.notification.events;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hireflow.contracts.events.ApplicationEvent;
import com.hireflow.contracts.events.ApplicationEventPayload;
import com.hireflow.contracts.events.ApplicationEventType;

class ApplicationEventHandlerTest {

    @Test
    void duplicateEventIsHandledWithoutRepeatingProcessing() {
        UUID applicationId = UUID.randomUUID();
        ApplicationEvent event = new ApplicationEvent(UUID.randomUUID(),
                ApplicationEventType.APPLICATION_SUBMITTED, 1, Instant.now(), applicationId,
                UUID.randomUUID(), new ApplicationEventPayload(applicationId, UUID.randomUUID(),
                        UUID.randomUUID(), null, "APPLIED"));
        ApplicationEventHandler handler = new ApplicationEventHandler();

        assertDoesNotThrow(() -> handler.handle(event));
        assertDoesNotThrow(() -> handler.handle(event));
    }

    @Test
    void invalidVersionIsRejected() {
        ApplicationEvent event = new ApplicationEvent(UUID.randomUUID(),
                ApplicationEventType.APPLICATION_SUBMITTED, 9, Instant.now(), UUID.randomUUID(),
                UUID.randomUUID(), null);

        assertThrows(IllegalArgumentException.class,
                () -> new ApplicationEventHandler().handle(event));
    }
}
