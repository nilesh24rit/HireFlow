package com.hireflow.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the external routes exposed by the HireFlow API gateway.
 *
 * <p>Each route maps a public {@code /api/...} path onto the internal service that owns it.
 * The incoming path is forwarded unchanged because the downstream controllers already use
 * the same {@code /api/...} contract, so no path rewriting filters are registered.</p>
 */
@Configuration
public class GatewayRouteConfiguration {

    @Bean
    public RouteLocator apiRoutes(
            RouteLocatorBuilder builder,
            @Value("${hireflow.services.auth-service-uri:http://localhost:8081}") String authServiceUri,
            @Value("${hireflow.services.candidate-service-uri:http://localhost:8082}") String candidateServiceUri,
            @Value("${hireflow.services.job-service-uri:http://localhost:8083}") String jobServiceUri,
            @Value("${hireflow.services.application-service-uri:http://localhost:8084}") String applicationServiceUri) {
        return builder.routes()
                .route("auth-service", route -> route
                        .path("/api/users/**")
                        .uri(authServiceUri))
                .route("candidate-service", route -> route
                        .path("/api/candidates/**")
                        .uri(candidateServiceUri))
                .route("job-service", route -> route
                        .path("/api/jobs/**")
                        .uri(jobServiceUri))
                .route("application-service", route -> route
                        .path("/api/applications/**")
                        .uri(applicationServiceUri))
                .build();
    }
}
