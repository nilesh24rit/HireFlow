package com.hireflow.analytics.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;

class KafkaConsumerConfigurationTest {

    @Test
    void usesAnIndependentAnalyticsConsumerGroup() {
        DefaultKafkaConsumerFactory<String, Object> factory =
                (DefaultKafkaConsumerFactory<String, Object>) new KafkaConsumerConfiguration()
                        .kafkaConsumerFactory(new KafkaProperties(),
                                new KafkaInfrastructureProperties());

        Map<String, Object> configs = factory.getConfigurationProperties();

        assertThat(configs).containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "hireflow-analytics");
        assertThat(configs).containsEntry(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class);
    }
}
