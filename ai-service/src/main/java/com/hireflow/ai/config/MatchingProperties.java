package com.hireflow.ai.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MatchingProperties {

    private final URI candidateServiceUrl;
    private final URI jobServiceUrl;
    private final URI applicationServiceUrl;
    private final Duration timeout;

    public MatchingProperties(
            @Value("${hireflow.services.candidate-service.url:http://localhost:8082}") URI candidateServiceUrl,
            @Value("${hireflow.services.job-service.url:http://localhost:8083}") URI jobServiceUrl,
            @Value("${hireflow.services.application-service.url:http://localhost:8084}") URI applicationServiceUrl,
            @Value("${hireflow.matching.service-timeout:5s}") Duration timeout) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Matching service timeout must be positive");
        }
        this.candidateServiceUrl = requireHttp(candidateServiceUrl);
        this.jobServiceUrl = requireHttp(jobServiceUrl);
        this.applicationServiceUrl = requireHttp(applicationServiceUrl);
        this.timeout = timeout;
    }

    public URI candidateServiceUrl() {
        return candidateServiceUrl;
    }

    public URI jobServiceUrl() {
        return jobServiceUrl;
    }

    public URI applicationServiceUrl() {
        return applicationServiceUrl;
    }

    public Duration timeout() {
        return timeout;
    }

    private URI requireHttp(URI uri) {
        if (uri == null || (!"http".equalsIgnoreCase(uri.getScheme())
                && !"https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("Matching service URLs must use http or https");
        }
        return uri;
    }
}
