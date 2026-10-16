package com.hireflow.application.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hireflow.kafka")
public class KafkaInfrastructureProperties {

    private String applicationsTopic = "hireflow.events.applications.v1";
    private int topicPartitions = 3;
    private short topicReplicationFactor = 1;
    private String producerClientId = "application-service";
    private Duration deliveryTimeout = Duration.ofSeconds(120);
    private int retries = 5;

    public String getApplicationsTopic() {
        return applicationsTopic;
    }

    public void setApplicationsTopic(String applicationsTopic) {
        this.applicationsTopic = applicationsTopic;
    }

    public int getTopicPartitions() {
        return topicPartitions;
    }

    public void setTopicPartitions(int topicPartitions) {
        this.topicPartitions = topicPartitions;
    }

    public short getTopicReplicationFactor() {
        return topicReplicationFactor;
    }

    public void setTopicReplicationFactor(short topicReplicationFactor) {
        this.topicReplicationFactor = topicReplicationFactor;
    }

    public String getProducerClientId() {
        return producerClientId;
    }

    public void setProducerClientId(String producerClientId) {
        this.producerClientId = producerClientId;
    }

    public Duration getDeliveryTimeout() {
        return deliveryTimeout;
    }

    public void setDeliveryTimeout(Duration deliveryTimeout) {
        this.deliveryTimeout = deliveryTimeout;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }
}
