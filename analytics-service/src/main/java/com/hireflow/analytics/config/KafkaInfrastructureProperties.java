package com.hireflow.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hireflow.kafka")
public class KafkaInfrastructureProperties {

    private String consumerGroup = "hireflow-analytics";
    private String consumerClientId = "analytics-service";
    private int maxPollRecords = 100;
    private int maxPollIntervalMs = 300000;

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String getConsumerClientId() {
        return consumerClientId;
    }

    public void setConsumerClientId(String consumerClientId) {
        this.consumerClientId = consumerClientId;
    }

    public int getMaxPollRecords() {
        return maxPollRecords;
    }

    public void setMaxPollRecords(int maxPollRecords) {
        this.maxPollRecords = maxPollRecords;
    }

    public int getMaxPollIntervalMs() {
        return maxPollIntervalMs;
    }

    public void setMaxPollIntervalMs(int maxPollIntervalMs) {
        this.maxPollIntervalMs = maxPollIntervalMs;
    }
}
