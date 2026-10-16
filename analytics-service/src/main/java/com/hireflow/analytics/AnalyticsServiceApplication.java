package com.hireflow.analytics;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.hireflow.analytics.config.KafkaInfrastructureProperties;

@SpringBootApplication
@EnableConfigurationProperties(KafkaInfrastructureProperties.class)
public class AnalyticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AnalyticsServiceApplication.class);
        application.setDefaultProperties(Map.of(
                "spring.jpa.hibernate.ddl-auto", "none",
                "spring.jpa.open-in-view", "false"));
        application.run(args);
    }
}
