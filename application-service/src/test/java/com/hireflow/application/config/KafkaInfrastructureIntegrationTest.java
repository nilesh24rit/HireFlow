package com.hireflow.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class KafkaInfrastructureIntegrationTest {

    private static final String TOPIC = "hireflow.events.applications.v1";

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Test
    void publishesAndConsumesJsonWithIndependentGroups() throws Exception {
        KafkaProperties bootProperties = new KafkaProperties();
        bootProperties.setBootstrapServers(List.of(KAFKA.getBootstrapServers()));
        KafkaInfrastructureProperties infrastructureProperties = new KafkaInfrastructureProperties();
        infrastructureProperties.setApplicationsTopic(TOPIC);

        try (AdminClient adminClient = AdminClient.create(
                Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            adminClient.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1)))
                    .all().get(10, TimeUnit.SECONDS);

            ProducerFactory<String, Object> producerFactory =
                    new KafkaProducerConfiguration().kafkaProducerFactory(
                            bootProperties, infrastructureProperties);
            KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory);
            template.send(TOPIC, "application-1",
                    Map.of("applicationId", UUID.randomUUID().toString()))
                    .get(10, TimeUnit.SECONDS);

            assertThat(readOne(bootProperties, "hireflow-notification"))
                    .isPresent();
            assertThat(readOne(bootProperties, "hireflow-analytics"))
                    .isPresent();
        }
    }

    private java.util.Optional<ConsumerRecord<String, Map<String, Object>>> readOne(
            KafkaProperties bootProperties, String groupId) {
        Map<String, Object> consumerProperties = new HashMap<>(
                bootProperties.buildConsumerProperties());
        consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                JsonDeserializer.class);
        consumerProperties.put(JsonDeserializer.TRUSTED_PACKAGES, "java.util");
        consumerProperties.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        consumerProperties.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.LinkedHashMap");
        try (Consumer<String, Map<String, Object>> consumer =
                     new DefaultKafkaConsumerFactory<String, Map<String, Object>>(consumerProperties)
                             .createConsumer()) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, Map<String, Object>> record :
                        consumer.poll(Duration.ofMillis(250))) {
                    return java.util.Optional.of(record);
                }
            }
            return java.util.Optional.empty();
        }
    }
}
