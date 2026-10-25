package com.hireflow.analytics.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hireflow.contracts.events.ApplicationEvent;
import com.hireflow.analytics.repository.ApplicationProjectionRepository;

@Component
public class ApplicationEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEventHandler.class);
    private final EventIdempotencyStore idempotencyStore;
    private final ApplicationProjectionRepository projectionRepository;

    public ApplicationEventHandler(EventIdempotencyStore idempotencyStore,
            ApplicationProjectionRepository projectionRepository) {
        this.idempotencyStore = idempotencyStore;
        this.projectionRepository = projectionRepository;
    }

    ApplicationEventHandler() {
        this.idempotencyStore = new EventIdempotencyStore(null) {
            private final Set<UUID> eventIds = ConcurrentHashMap.newKeySet();

            @Override
            public boolean recordIfNew(UUID eventId) {
                return eventIds.add(eventId);
            }
        };
        this.projectionRepository = null;
    }

    @Transactional
    public void handle(ApplicationEvent event) {
        event.validate();
        if (!idempotencyStore.recordIfNew(event.eventId())) {
            log.debug("Ignoring duplicate application event eventId={}", event.eventId());
            return;
        }
        if (projectionRepository == null) {
            return;
        }

        var existing = projectionRepository.findByApplicationId(event.payload().applicationId());
        if (existing.isEmpty()) {
            projectionRepository.insert(event, 0);
        } else if (event.occurredAt().isAfter(existing.get().lastStatusChangedAt())) {
            projectionRepository.update(event, 0);
        }
        log.debug("Applied application analytics event eventId={} type={}",
                event.eventId(), event.eventType());
    }
}
