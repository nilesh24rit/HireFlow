package com.hireflow.job;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JobServiceApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(JobServiceApplication.class);
        application.setDefaultProperties(Map.of(
                "spring.jpa.hibernate.ddl-auto", "none",
                "spring.jpa.open-in-view", "false"));
        application.run(args);
    }
}
