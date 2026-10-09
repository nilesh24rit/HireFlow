package com.hireflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

/**
 * Verifies that the gateway application starts and exposes exactly the four HireFlow
 * routes with stable route ids.
 */
@SpringBootTest
class GatewayStartupTests {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void contextLoadsWithFourRoutes() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes)
                .isNotNull()
                .extracting(Route::getId)
                .containsExactlyInAnyOrder(
                        "auth-service",
                        "candidate-service",
                        "job-service",
                        "application-service");
    }
}
