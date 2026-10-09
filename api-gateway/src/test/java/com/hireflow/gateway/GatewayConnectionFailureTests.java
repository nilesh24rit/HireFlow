package com.hireflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifies that a request whose downstream service cannot be reached produces a clean
 * gateway error instead of an internal failure response.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayConnectionFailureTests {

    @DynamicPropertySource
    static void closedAuthPort(DynamicPropertyRegistry registry) {
        registry.add("hireflow.services.auth-service-uri", () -> {
            try (ServerSocket socket = new ServerSocket(0)) {
                return "http://localhost:" + socket.getLocalPort();
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    @LocalServerPort
    private int port;

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void returnsCleanGatewayErrorWhenDownstreamIsUnreachable() {
        String body = client().get().uri("/api/users/1").exchange()
                .expectStatus().isEqualTo(502)
                .expectBody(String.class)
                .returnResult().getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body).contains("\"status\":502").contains("Upstream service is unavailable");
        assertThat(body)
                .doesNotContain("Exception")
                .doesNotContain("at com.hireflow")
                .doesNotContain("localhost")
                .doesNotContain("stackTrace");
    }
}
