package com.hireflow.application.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireflow.application.config.KafkaInfrastructureProperties;
import com.hireflow.application.entity.ApplicationOutbox;
import com.hireflow.application.repository.ApplicationOutboxRepository;
import com.hireflow.contracts.events.ApplicationEvent;
import com.hireflow.contracts.events.ApplicationEventPayload;
import com.hireflow.contracts.events.ApplicationEventType;

@ExtendWith(MockitoExtension.class)
class ApplicationOutboxPublisherTest {

    @Mock
    private ApplicationOutboxRepository repository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaInfrastructureProperties properties;
    private ApplicationOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        properties = new KafkaInfrastructureProperties();
        publisher = new ApplicationOutboxPublisher(repository, kafkaTemplate, properties,
                new ObjectMapper().findAndRegisterModules());
        when(repository.findStaleClaims(any())).thenReturn(List.of());
    }

    @Test
    void marksOutboxPublishedOnlyAfterKafkaFutureCompletes() throws Exception {
        ApplicationOutbox outbox = outbox();
        when(repository.claimEligible(any(), eq(properties.getOutboxBatchSize())))
                .thenReturn(List.of(outbox));
        CompletableFuture<SendResult<String, Object>> brokerAcknowledgement = new CompletableFuture<>();
        when(kafkaTemplate.send(eq(properties.getApplicationsTopic()),
                eq(outbox.getAggregateId().toString()), any())).thenReturn(brokerAcknowledgement);

        Thread publishing = Thread.startVirtualThread(publisher::publishEligible);
        Thread.sleep(50);
        assertThat(outbox.getStatus()).isEqualTo(ApplicationOutbox.DeliveryStatus.IN_PROGRESS);
        brokerAcknowledgement.complete(null);
        publishing.join();

        assertThat(outbox.getStatus()).isEqualTo(ApplicationOutbox.DeliveryStatus.PUBLISHED);
        verify(repository).save(outbox);
    }

    @Test
    void failedKafkaPublicationRemainsPendingWithBackoff() {
        ApplicationOutbox outbox = outbox();
        when(repository.claimEligible(any(), eq(properties.getOutboxBatchSize())))
                .thenReturn(List.of(outbox));
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        publisher.publishEligible();

        assertThat(outbox.getStatus()).isEqualTo(ApplicationOutbox.DeliveryStatus.PENDING);
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(outbox.getLastError()).contains("broker unavailable");
    }

    private ApplicationOutbox outbox() {
        UUID applicationId = UUID.randomUUID();
        ApplicationEvent event = new ApplicationEvent(
                UUID.randomUUID(), ApplicationEventType.APPLICATION_SUBMITTED,
                ApplicationEvent.CURRENT_VERSION, Instant.now(), applicationId, UUID.randomUUID(),
                new ApplicationEventPayload(applicationId, UUID.randomUUID(), UUID.randomUUID(),
                        null, "APPLIED"));
        return new ApplicationOutbox(event.eventId(), applicationId, event.eventType().name(),
                event.eventVersion(), write(event), event.occurredAt());
    }

    private String write(ApplicationEvent event) {
        try {
            return new ObjectMapper().findAndRegisterModules().writeValueAsString(event);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
