package com.hireflow.application.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireflow.application.config.KafkaInfrastructureProperties;
import com.hireflow.application.dto.ApplicationResponse;
import com.hireflow.application.entity.ApplicationOutbox;
import com.hireflow.application.entity.ApplicationStatus;
import com.hireflow.application.repository.ApplicationOutboxRepository;
import com.hireflow.contracts.events.ApplicationEvent;

class ApplicationEventOutboxPersistenceTest {

    @Test
    void persistsSerializedEventWithStableEventIdInsteadOfPublishing() throws Exception {
        ApplicationOutboxRepository repository = org.mockito.Mockito.mock(ApplicationOutboxRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = org.mockito.Mockito.mock(KafkaTemplate.class);
        ApplicationEventPublisher publisher = new ApplicationEventPublisher(repository,
                new ObjectMapper().findAndRegisterModules(), new KafkaInfrastructureProperties());
        UUID applicationId = UUID.randomUUID();

        publisher.publishSubmitted(new ApplicationResponse(applicationId, UUID.randomUUID(),
                UUID.randomUUID(), ApplicationStatus.APPLIED, null, Instant.now(), Instant.now()));

        ArgumentCaptor<ApplicationOutbox> captor = ArgumentCaptor.forClass(ApplicationOutbox.class);
        verify(repository).save(captor.capture());
        org.mockito.Mockito.verifyNoInteractions(kafkaTemplate);

        ApplicationOutbox outbox = captor.getValue();
        ApplicationEvent event = new ObjectMapper().findAndRegisterModules()
                .readValue(outbox.getPayload(), ApplicationEvent.class);
        assertThat(outbox.getId()).isEqualTo(event.eventId());
        assertThat(outbox.getAggregateId()).isEqualTo(applicationId);
        assertThat(outbox.getStatus()).isEqualTo(ApplicationOutbox.DeliveryStatus.PENDING);
        assertThat(event.aggregateId()).isEqualTo(applicationId);
    }
}
