package com.hireflow.ai.matching.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.hireflow.ai.config.MatchingProperties;

@Configuration
public class MatchingClientConfiguration {

    @Bean
    RestClient matchingRestClient(RestClient.Builder builder, MatchingProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        return builder.requestFactory(requestFactory).build();
    }
}
