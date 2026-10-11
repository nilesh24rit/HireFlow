package com.hireflow.auth.config;

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
    public OpenAPI hireFlowAuthServiceApi() {
        return new OpenAPI().info(new Info()
                .title("HireFlow Auth Service API")
                .description("Registers HireFlow users, manages their profiles, and issues the "
                        + "access tokens every protected HireFlow endpoint accepts. Tokens are "
                        + "obtained either with email and password or by signing in with Google. "
                        + "Responses use JSON, and failures follow the common HireFlow error "
                        + "contract with timestamp, status, error, code, message and path.")
                .version("1.0.0")
                .license(new License()
                        .name("Apache License 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token issued by POST /api/auth/login "
                                        + "after email-and-password verification, or by "
                                        + "POST /api/auth/oauth2/token after signing in with "
                                        + "Google. Both produce the identical token. Send it "
                                        + "as 'Authorization: Bearer <accessToken>'.")));
    }
}
