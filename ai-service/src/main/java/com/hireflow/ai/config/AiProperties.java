package com.hireflow.ai.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiProperties {

    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final int maxOutputTokens;
    private final double temperature;
    private final long maxResumeBytes;
    private final int maxExtractedCharacters;

    public AiProperties(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String model,
            @Value("${hireflow.ai.request-timeout:30s}") Duration timeout,
            @Value("${hireflow.ai.max-output-tokens:1200}") int maxOutputTokens,
            @Value("${hireflow.ai.temperature:0.0}") double temperature,
            @Value("${hireflow.ai.max-resume-bytes:5242880}") long maxResumeBytes,
            @Value("${hireflow.ai.max-extracted-characters:100000}") int maxExtractedCharacters) {
        if (maxResumeBytes <= 0 || maxExtractedCharacters <= 0 || maxOutputTokens <= 0
                || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("AI processing limits must be positive");
        }
        if (temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("AI temperature must be between 0 and 2");
        }
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
        this.maxOutputTokens = maxOutputTokens;
        this.temperature = temperature;
        this.maxResumeBytes = maxResumeBytes;
        this.maxExtractedCharacters = maxExtractedCharacters;
    }

    public String apiKey() {
        return apiKey;
    }

    public String model() {
        return model;
    }

    public Duration timeout() {
        return timeout;
    }

    public int maxOutputTokens() {
        return maxOutputTokens;
    }

    public double temperature() {
        return temperature;
    }

    public long maxResumeBytes() {
        return maxResumeBytes;
    }

    public int maxExtractedCharacters() {
        return maxExtractedCharacters;
    }
}
