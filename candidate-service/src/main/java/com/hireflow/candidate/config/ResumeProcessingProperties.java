package com.hireflow.candidate.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hireflow.ai")
public class ResumeProcessingProperties {

    private String baseUrl = "http://localhost:8086";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(35);
    private long maxResumeBytes = 5 * 1024 * 1024;
    private Duration proposalTtl = Duration.ofHours(24);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public long getMaxResumeBytes() {
        return maxResumeBytes;
    }

    public void setMaxResumeBytes(long maxResumeBytes) {
        this.maxResumeBytes = maxResumeBytes;
    }

    public Duration getProposalTtl() {
        return proposalTtl;
    }

    public void setProposalTtl(Duration proposalTtl) {
        this.proposalTtl = proposalTtl;
    }
}
