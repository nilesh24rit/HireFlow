package com.hireflow.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

class KafkaProducerConfigurationTest {

    @Test
    void configuresAnIdempotentBoundedJsonProducer() {
        KafkaInfrastructureProperties properties = new KafkaInfrastructureProperties();
        DefaultKafkaProducerFactory<String, Object> factory =
                (DefaultKafkaProducerFactory<String, Object>) new KafkaProducerConfiguration()
                        .kafkaProducerFactory(new KafkaProperties(), properties);

        Map<String, Object> configs = factory.getConfigurationProperties();

        assertThat(configs).containsEntry(ProducerConfig.ACKS_CONFIG, "all");
        assertThat(configs).containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        assertThat(configs).containsEntry(ProducerConfig.RETRIES_CONFIG, 5);
        assertThat(configs).containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonSerializer.class);
    }
}
