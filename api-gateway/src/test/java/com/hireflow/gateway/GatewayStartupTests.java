package com.hireflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

/**
 * Verifies that the gateway application starts and exposes exactly the HireFlow routes
 * with stable route ids: the auth API, resource routes, and the Google sign-in route.
 */
@SpringBootTest
class GatewayStartupTests {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void contextLoadsWithExactlyTheHireFlowRoutes() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes)
                .isNotNull()
                .extracting(Route::getId)
                .containsExactlyInAnyOrder(
                        "auth-service",
                        "auth-api",
                        "candidate-service",
                        "job-service",
                        "application-service",
                        "ai-service",
                        "notification-service",
                        "analytics-service",
                        "google-oauth2");
    }
}
