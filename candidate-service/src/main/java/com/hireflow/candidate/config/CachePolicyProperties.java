package com.hireflow.candidate.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hireflow.cache")
public class CachePolicyProperties {

    private String keyPrefix = "hireflow:candidate:";
    private Duration candidateProfileTtl = Duration.ofMinutes(15);
    private Duration candidateUserTtl = Duration.ofMinutes(15);

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Duration getCandidateProfileTtl() {
        return candidateProfileTtl;
    }

    public void setCandidateProfileTtl(Duration candidateProfileTtl) {
        this.candidateProfileTtl = candidateProfileTtl;
    }

    public Duration getCandidateUserTtl() {
        return candidateUserTtl;
    }

    public void setCandidateUserTtl(Duration candidateUserTtl) {
        this.candidateUserTtl = candidateUserTtl;
    }
}
