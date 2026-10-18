package com.hireflow.application.events;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.hireflow.application.config.KafkaInfrastructureProperties;
import com.hireflow.application.dto.ApplicationResponse;
import com.hireflow.application.entity.ApplicationStatus;
import com.hireflow.contracts.events.ApplicationEvent;
import com.hireflow.contracts.events.ApplicationEventPayload;
import com.hireflow.contracts.events.ApplicationEventType;

@Component
public class ApplicationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaInfrastructureProperties properties;

    public ApplicationEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
            KafkaInfrastructureProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public void publishSubmitted(ApplicationResponse application) {
        publish(application, null, application.status(), ApplicationEventType.APPLICATION_SUBMITTED);
    }

    public void publishStatusChanged(ApplicationResponse application, ApplicationStatus previousStatus) {
        publish(application, previousStatus, application.status(),
                ApplicationEventType.APPLICATION_STATUS_CHANGED);
    }

    public void publishWithdrawn(ApplicationResponse application) {
        publish(application, application.status(), ApplicationStatus.WITHDRAWN,
                ApplicationEventType.APPLICATION_WITHDRAWN);
    }

    private void publish(ApplicationResponse application, ApplicationStatus previousStatus,
            ApplicationStatus currentStatus, ApplicationEventType eventType) {
        ApplicationEventPayload payload = new ApplicationEventPayload(
                application.id(), application.candidateId(), application.jobId(),
                previousStatus == null ? null : previousStatus.name(), currentStatus.name());
        ApplicationEvent event = new ApplicationEvent(
                UUID.randomUUID(), eventType, ApplicationEvent.CURRENT_VERSION, Instant.now(),
                application.id(), UUID.randomUUID(), payload);
        event.validate();

        CompletableFuture<SendResult<String, Object>> send = kafkaTemplate.send(
                properties.getApplicationsTopic(), application.id().toString(), event);
        send.whenComplete((result, failure) -> {
            if (failure != null) {
                log.error("Application event send failed eventId={} type={} applicationId={}",
                        event.eventId(), event.eventType(), application.id(), failure);
                return;
            }
            RecordMetadata metadata = result.getRecordMetadata();
            log.info("Application event sent eventId={} type={} applicationId={} partition={} offset={}",
                    event.eventId(), event.eventType(), application.id(),
                    metadata.partition(), metadata.offset());
        });
    }
}
