package com.hireflow.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;

class KafkaTopicConfigurationTest {

    @Test
    void createsTheVersionedApplicationsTopic() {
        KafkaInfrastructureProperties properties = new KafkaInfrastructureProperties();

        NewTopic topic = new KafkaTopicConfiguration().applicationsEventsTopic(properties);

        assertThat(topic.name()).isEqualTo("hireflow.events.applications.v1");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }
}
