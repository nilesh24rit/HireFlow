package com.hireflow.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

class KafkaConsumerConfigurationTest {

    @Test
    void usesTheNotificationConsumerGroupAndBoundedRetries() {
        DefaultKafkaConsumerFactory<String, Object> factory =
                (DefaultKafkaConsumerFactory<String, Object>) new KafkaConsumerConfiguration()
                        .kafkaConsumerFactory(new KafkaProperties(),
                                new KafkaInfrastructureProperties());

        Map<String, Object> configs = factory.getConfigurationProperties();

        assertThat(configs).containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "hireflow-notification");
        assertThat(configs).containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        assertThat(configs).containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    }
}
