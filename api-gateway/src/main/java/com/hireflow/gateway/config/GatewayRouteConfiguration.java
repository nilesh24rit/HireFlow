package com.hireflow.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Route table of the HireFlow API gateway.
 *
 * <p>Each entry names the route id, the public path predicate and the target service URI.
 * Paths are forwarded untouched because the downstream controllers already expose the same
 * {@code /api/...} contract, so no path rewriting filters are needed:</p>
 *
 * <pre>
 * auth-service        /api/users/**         -&gt; auth-service
 * candidate-service   /api/candidates/**    -&gt; candidate-service
 * job-service         /api/jobs/**          -&gt; job-service
 * application-service /api/applications/**  -&gt; application-service
 * </pre>
 *
 * <p><b>Authentication (Step 13).</b> Routes carry no JWT filter and no signing key: the
 * {@code Authorization} header is forwarded byte-for-byte, and the service behind the route
 * is the only component that validates a token. Duplicating validation here would give the
 * edge and the service two opinions about the same credential, and would force a secret to
 * exist in a module that never needs one. A rejected token therefore comes back through
 * the gateway as the downstream 401, unchanged.</p>
 */
@Configuration
@EnableConfigurationProperties(HireFlowServiceUris.class)
public class GatewayRouteConfiguration {

    static final String AUTH_SERVICE_ROUTE_ID = "auth-service";
    static final String CANDIDATE_SERVICE_ROUTE_ID = "candidate-service";
    static final String JOB_SERVICE_ROUTE_ID = "job-service";
    static final String APPLICATION_SERVICE_ROUTE_ID = "application-service";

    static final String USERS_PATH = "/api/users/**";
    static final String CANDIDATES_PATH = "/api/candidates/**";
    static final String JOBS_PATH = "/api/jobs/**";
    static final String APPLICATIONS_PATH = "/api/applications/**";

    @Bean
    public RouteLocator apiRoutes(RouteLocatorBuilder builder, HireFlowServiceUris services) {
        return builder.routes()
                .route(AUTH_SERVICE_ROUTE_ID, route -> route
                        .path(USERS_PATH)
                        .uri(services.authServiceUri()))
                .route(CANDIDATE_SERVICE_ROUTE_ID, route -> route
                        .path(CANDIDATES_PATH)
                        .uri(services.candidateServiceUri()))
                .route(JOB_SERVICE_ROUTE_ID, route -> route
                        .path(JOBS_PATH)
                        .uri(services.jobServiceUri()))
                .route(APPLICATION_SERVICE_ROUTE_ID, route -> route
                        .path(APPLICATIONS_PATH)
                        .uri(services.applicationServiceUri()))
                .build();
    }
}
