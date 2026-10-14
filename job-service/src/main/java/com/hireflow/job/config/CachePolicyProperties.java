package com.hireflow.job.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hireflow.cache")
public class CachePolicyProperties {

    private String keyPrefix = "hireflow:job:";
    private Duration jobDetailsTtl = Duration.ofMinutes(10);

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Duration getJobDetailsTtl() {
        return jobDetailsTtl;
    }

    public void setJobDetailsTtl(Duration jobDetailsTtl) {
        this.jobDetailsTtl = jobDetailsTtl;
    }
}
