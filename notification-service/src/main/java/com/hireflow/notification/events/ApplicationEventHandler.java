package com.hireflow.notification.events;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hireflow.contracts.events.ApplicationEvent;
import com.hireflow.contracts.events.ApplicationEventType;
import com.hireflow.notification.domain.DeliveryStatus;
import com.hireflow.notification.domain.Notification;
import com.hireflow.notification.domain.NotificationType;
import com.hireflow.notification.recipient.Recipient;
import com.hireflow.notification.recipient.RecipientResolver;
import com.hireflow.notification.repository.NotificationRepository;
import com.hireflow.notification.templates.NotificationTemplate;
import com.hireflow.notification.templates.NotificationTemplateRenderer;

@Component
public class ApplicationEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEventHandler.class);
    private final EventIdempotencyStore idempotencyStore;
    private final NotificationRepository notificationRepository;
    private final RecipientResolver recipientResolver;
    private final NotificationTemplateRenderer templateRenderer;

    @Autowired
    public ApplicationEventHandler(EventIdempotencyStore idempotencyStore,
            NotificationRepository notificationRepository, RecipientResolver recipientResolver,
            NotificationTemplateRenderer templateRenderer) {
        this.idempotencyStore = idempotencyStore;
        this.notificationRepository = notificationRepository;
        this.recipientResolver = recipientResolver;
        this.templateRenderer = templateRenderer;
    }

    ApplicationEventHandler() {
        this.idempotencyStore = new EventIdempotencyStoreForTests();
        this.notificationRepository = null;
        this.recipientResolver = null;
        this.templateRenderer = null;
    }

    public void handle(ApplicationEvent event) {
        event.validate();
        if (notificationRepository == null) {
            if (!idempotencyStore.recordIfNew(event.eventId())) {
                log.debug("Ignoring duplicate application event eventId={}", event.eventId());
            }
            return;
        }
        for (NotificationType type : notificationTypes(event.eventType())) {
            NotificationTemplate template = templateRenderer.render(event, type);
            for (Recipient recipient : recipientResolver.resolve(event, type)) {
                notificationRepository.save(new Notification(
                        UUID.randomUUID(), event.eventId(), recipient.userId(), type,
                        template.subject(), template.message(), "EMAIL", DeliveryStatus.PENDING,
                        0, java.time.Instant.now(), null, java.time.Instant.now(), null, false));
            }
        }
        if (!idempotencyStore.recordIfNew(event.eventId())) {
            log.debug("Notification records already existed for duplicate event eventId={}",
                    event.eventId());
        }
        log.info("Prepared notifications for application event eventId={} type={} applicationId={}",
                event.eventId(), event.eventType(), event.aggregateId());
    }

    private static NotificationType[] notificationTypes(ApplicationEventType eventType) {
        return switch (eventType) {
            case APPLICATION_SUBMITTED -> new NotificationType[] {
                    NotificationType.APPLICATION_SUBMITTED_CANDIDATE,
                    NotificationType.APPLICATION_SUBMITTED_RECRUITER };
            case APPLICATION_STATUS_CHANGED -> new NotificationType[] {
                    NotificationType.APPLICATION_STATUS_CHANGED };
            case APPLICATION_WITHDRAWN -> new NotificationType[] {
                    NotificationType.APPLICATION_WITHDRAWN };
        };
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
