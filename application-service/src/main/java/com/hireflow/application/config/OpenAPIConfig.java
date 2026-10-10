package com.hireflow.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenAPIConfig {

    /** Name of the bearer security scheme referenced by protected operations. */
    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI hireFlowApplicationServiceApi() {
        return new OpenAPI().info(new Info()
                .title("HireFlow Application Service API")
                .description("Manages candidate job applications and their status transitions. "
                        + "Responses use JSON, and failures follow the common HireFlow error contract "
                        + "with timestamp, status, error, code, message and path.")
                .version("1.0.0")
                .license(new License()
                        .name("Apache License 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token issued by the HireFlow auth service "
                                        + "(POST /api/auth/login). "
                                        + "Send it as 'Authorization: Bearer <accessToken>'.")));
    }
}
