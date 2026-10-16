package com.hireflow.application.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfiguration {

    @Bean
    NewTopic applicationsEventsTopic(KafkaInfrastructureProperties properties) {
        if (properties.getApplicationsTopic() == null
                || properties.getApplicationsTopic().isBlank()) {
            throw new IllegalArgumentException("Kafka applications topic must be configured");
        }
        if (properties.getTopicPartitions() < 1) {
            throw new IllegalArgumentException("Kafka topic partitions must be greater than zero");
        }
        if (properties.getTopicReplicationFactor() < 1) {
            throw new IllegalArgumentException("Kafka topic replication factor must be greater than zero");
        }
        return TopicBuilder.name(properties.getApplicationsTopic())
                .partitions(properties.getTopicPartitions())
                .replicas(properties.getTopicReplicationFactor())
                .build();
    }
}
