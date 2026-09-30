package com.hireflow.candidate;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CandidateServiceApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(CandidateServiceApplication.class);
        application.setDefaultProperties(Map.of(
                "spring.jpa.hibernate.ddl-auto", "none",
                "spring.jpa.open-in-view", "false"));
        application.run(args);
    }
}
