package com.hireflow.application;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.hireflow.application.config.KafkaInfrastructureProperties;

@SpringBootApplication
@EnableConfigurationProperties(KafkaInfrastructureProperties.class)
@EnableScheduling
public class ApplicationServiceApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ApplicationServiceApplication.class);
        application.setDefaultProperties(Map.of(
                "spring.jpa.hibernate.ddl-auto", "none",
                "spring.jpa.open-in-view", "false"));
        application.run(args);
    }
}
