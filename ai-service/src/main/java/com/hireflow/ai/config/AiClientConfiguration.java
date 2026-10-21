package com.hireflow.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiClientConfiguration {

    @Bean
    ChatClient resumeChatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
