package com.hireflow.ai.matching.client;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.hireflow.ai.config.MatchingProperties;

@Configuration
public class MatchingClientConfiguration {

    @Bean
    RestClient matchingRestClient(RestClient.Builder builder, MatchingProperties properties) {
        return builder.requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
            setConnectTimeout(properties.timeout());
            setReadTimeout(properties.timeout());
        }}).build();
    }
}
