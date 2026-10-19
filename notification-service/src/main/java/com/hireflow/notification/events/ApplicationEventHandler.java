package com.hireflow.notification.events;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.hireflow.contracts.events.ApplicationEvent;

@Component
public class ApplicationEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEventHandler.class);
    private final EventIdempotencyStore idempotencyStore;

    public ApplicationEventHandler(EventIdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
    }

    ApplicationEventHandler() {
        this.idempotencyStore = new EventIdempotencyStoreForTests();
    }

    public void handle(ApplicationEvent event) {
        event.validate();
        if (!idempotencyStore.recordIfNew(event.eventId())) {
            log.debug("Ignoring duplicate application event eventId={}", event.eventId());
            return;
        }
        log.info("Prepared notification for application event eventId={} type={} applicationId={}",
                event.eventId(), event.eventType(), event.aggregateId());
    }

    private static final class EventIdempotencyStoreForTests extends EventIdempotencyStore {
        private final java.util.Set<UUID> eventIds = ConcurrentHashMap.newKeySet();

        private EventIdempotencyStoreForTests() {
            super(null);
        }

        @Override
        public boolean recordIfNew(UUID eventId) {
            return eventIds.add(eventId);
        }
    }
}
