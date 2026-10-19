package com.hireflow.application.events;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireflow.application.config.KafkaInfrastructureProperties;
import com.hireflow.application.entity.ApplicationOutbox;
import com.hireflow.application.repository.ApplicationOutboxRepository;
import com.hireflow.contracts.events.ApplicationEvent;

@Component
public class ApplicationOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(ApplicationOutboxPublisher.class);

    private final ApplicationOutboxRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaInfrastructureProperties properties;
    private final ObjectMapper objectMapper;

    public ApplicationOutboxPublisher(ApplicationOutboxRepository repository,
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaInfrastructureProperties properties, ObjectMapper objectMapper) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "#{@kafkaInfrastructureProperties.outboxPollInterval.toMillis()}")
    public void publishEligible() {
        recoverStaleClaims();
        for (ApplicationOutbox outbox : claimBatch()) {
            publishOne(outbox);
        }
    }

    @Transactional
    List<ApplicationOutbox> claimBatch() {
        Instant now = Instant.now();
        List<ApplicationOutbox> records = repository.claimEligible(
                now, properties.getOutboxBatchSize());
        records.forEach(outbox -> outbox.markInProgress(
                now.plus(properties.getOutboxClaimTimeout())));
        return records;
    }

    @Transactional
    void recoverStaleClaims() {
        Instant now = Instant.now();
        repository.findStaleClaims(now.minus(properties.getOutboxClaimTimeout()))
                .forEach(outbox -> outbox.recover(now, "Recovered stale publisher claim"));
    }

    private void publishOne(ApplicationOutbox outbox) {
        try {
            ApplicationEvent event = objectMapper.readValue(outbox.getPayload(), ApplicationEvent.class);
            event.validate();
            kafkaTemplate.send(properties.getApplicationsTopic(),
                    outbox.getAggregateId().toString(), event).get(
                            properties.getDeliveryTimeout().toMillis(), TimeUnit.MILLISECONDS);
            markPublished(outbox);
        } catch (Exception ex) {
            String message = sanitize(ex);
            log.warn("Application outbox publication failed eventId={} attempt={} reason={}",
                    outbox.getId(), outbox.getAttemptCount(), message);
            markRetry(outbox, message);
        }
    }

    @Transactional
    void markPublished(ApplicationOutbox outbox) {
        outbox.markPublished(Instant.now());
        repository.save(outbox);
    }

    @Transactional
    void markRetry(ApplicationOutbox outbox, String error) {
        long multiplier = 1L << Math.min(outbox.getAttemptCount() - 1, 20);
        long delay = Math.min(
                properties.getOutboxRetryDelay().toMillis() * multiplier,
                properties.getOutboxMaxRetryDelay().toMillis());
        outbox.markRetry(Instant.now().plusMillis(delay), error);
        repository.save(outbox);
    }

    private String sanitize(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        String normalized = message.replaceAll("[\\r\\n\\t]+", " ");
        return normalized.substring(0, Math.min(normalized.length(), 500));
    }
}
