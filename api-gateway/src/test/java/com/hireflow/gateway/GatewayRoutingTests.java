package com.hireflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * End-to-end routing tests of the gateway against mock downstream services, so no real
 * service or database is required.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTests {

    private static final MockDownstream AUTH = MockDownstream.start("auth-service");
    private static final MockDownstream CANDIDATE = MockDownstream.start("candidate-service");
    private static final MockDownstream JOB = MockDownstream.start("job-service");
    private static final MockDownstream APPLICATION = MockDownstream.start("application-service");

    @DynamicPropertySource
    static void serviceUris(DynamicPropertyRegistry registry) {
        registry.add("hireflow.services.auth-service-uri", AUTH::uri);
        registry.add("hireflow.services.candidate-service-uri", CANDIDATE::uri);
        registry.add("hireflow.services.job-service-uri", JOB::uri);
        registry.add("hireflow.services.application-service-uri", APPLICATION::uri);
    }

    @AfterAll
    static void stopDownstreams() {
        AUTH.stop();
        CANDIDATE.stop();
        JOB.stop();
        APPLICATION.stop();
    }

    @LocalServerPort
    private int port;

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void routesUsersPathToAuthService() {
        client().get().uri("/api/users/42").exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("service=auth-service")
                        .contains("path=/api/users/42"));
    }

    @Test
    void routesCandidatesPathToCandidateService() {
        client().get().uri("/api/candidates/7").exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("service=candidate-service")
                        .contains("path=/api/candidates/7"));
    }

    @Test
    void routesJobsPathToJobService() {
        client().get().uri("/api/jobs/9").exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("service=job-service")
                        .contains("path=/api/jobs/9"));
    }

    @Test
    void routesApplicationsPathToApplicationService() {
        client().get().uri("/api/applications/3").exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("service=application-service")
                        .contains("path=/api/applications/3"));
    }

    @Test
    void preservesHttpMethod() {
        client().post().uri("/api/candidates").contentType(MediaType.TEXT_PLAIN)
                .bodyValue("candidate payload").exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("service=candidate-service")
                        .contains("method=POST")
                        .contains("body=candidate payload"));

        client().put().uri("/api/users/42").exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("method=PUT"));

        client().delete().uri("/api/jobs/9").exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("method=DELETE"));
    }

    @Test
    void preservesQueryString() {
        client().get().uri(uriBuilder -> uriBuilder.path("/api/jobs").queryParam("page", "0").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("query=page=0"));
    }

    @Test
    void forwardsDownstreamStatusCodesUnchanged() {
        List<Integer> codes = List.of(400, 404, 409, 500);
        for (int code : codes) {
            client().get().uri("/api/jobs/error/" + code).exchange()
                    .expectStatus().isEqualTo(code)
                    .expectBody(String.class)
                    .value(body -> assertThat(body).isEqualTo("downstream-error " + code));
        }
    }

    @Test
    void passesSecurityResponsesThroughUntouched() {
        // The gateway performs no authentication, so a deliberate 401 from
        // auth-service (error contract plus Bearer challenge) must reach the client as-is.
        client().get().uri("/api/users/security/401").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("WWW-Authenticate")
                .expectHeader().valueEquals("Content-Type", "application/json")
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("\"code\":\"UNAUTHENTICATED\"")
                        .contains("\"message\":\"Authentication required\"")
                        .contains("\"path\":\"/api/users/security/401\"")
                        .doesNotContain("gateway"));
    }

    @Test
    void unknownRouteReturnsNotFound() {
        client().get().uri("/api/unknown/thing").exchange()
                .expectStatus().isNotFound();

        client().get().uri("/not-routed").exchange()
                .expectStatus().isNotFound();
    }
}
