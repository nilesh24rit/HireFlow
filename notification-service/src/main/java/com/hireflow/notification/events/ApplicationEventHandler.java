package com.hireflow.notification.events;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.hireflow.contracts.events.ApplicationEvent;

@Component
public class ApplicationEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEventHandler.class);
    private final Set<UUID> processedEventIds = ConcurrentHashMap.newKeySet();

    public void handle(ApplicationEvent event) {
        event.validate();
        if (!processedEventIds.add(event.eventId())) {
            log.debug("Ignoring duplicate application event eventId={}", event.eventId());
            return;
        }
        log.info("Prepared notification for application event eventId={} type={} applicationId={}",
                event.eventId(), event.eventType(), event.aggregateId());
    }
}
