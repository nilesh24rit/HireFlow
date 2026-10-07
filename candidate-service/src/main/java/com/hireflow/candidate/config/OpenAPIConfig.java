package com.hireflow.candidate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI hireFlowCandidateServiceApi() {
        return new OpenAPI().info(new Info()
                .title("HireFlow Candidate Service API")
                .description("Manages candidate profiles and their skills. "
                        + "Responses use JSON, and failures follow the common HireFlow error contract "
                        + "with timestamp, status, error, code, message and path.")
                .version("1.0.0")
                .license(new License()
                        .name("Apache License 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
