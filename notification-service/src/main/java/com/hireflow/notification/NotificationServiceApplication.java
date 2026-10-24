package com.hireflow.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.hireflow.notification.config.KafkaInfrastructureProperties;
import com.hireflow.notification.config.MailInfrastructureProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({KafkaInfrastructureProperties.class, MailInfrastructureProperties.class})
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
