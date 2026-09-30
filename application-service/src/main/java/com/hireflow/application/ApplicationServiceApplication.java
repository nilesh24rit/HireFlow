package com.hireflow.application;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApplicationServiceApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ApplicationServiceApplication.class);
        application.setDefaultProperties(Map.of(
                "spring.jpa.hibernate.ddl-auto", "none",
                "spring.jpa.open-in-view", "false"));
        application.run(args);
    }
}
