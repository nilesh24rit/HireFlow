package com.hireflow.application.events;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.hireflow.application.config.KafkaInfrastructureProperties;
import com.hireflow.application.dto.ApplicationResponse;
import com.hireflow.application.entity.ApplicationStatus;

class ApplicationEventPublisherTest {

    @Test
    void publishesWithApplicationIdAsKeyAndConfiguredTopic() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> template = org.mockito.Mockito.mock(KafkaTemplate.class);
        KafkaInfrastructureProperties properties = new KafkaInfrastructureProperties();
        UUID applicationId = UUID.randomUUID();
        org.mockito.Mockito.when(template.send(eq(properties.getApplicationsTopic()),
                eq(applicationId.toString()), org.mockito.ArgumentMatchers.any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        new ApplicationEventPublisher(template, properties).publishSubmitted(new ApplicationResponse(
                applicationId, UUID.randomUUID(), UUID.randomUUID(), ApplicationStatus.APPLIED,
                null, Instant.now(), Instant.now()));

        verify(template).send(eq(properties.getApplicationsTopic()), eq(applicationId.toString()),
                org.mockito.ArgumentMatchers.any());
    }
}
