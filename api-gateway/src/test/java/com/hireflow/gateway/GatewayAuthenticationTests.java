package com.hireflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Authentication behavior of the gateway.
 *
 * <p>The gateway is a pass-through for credentials: it forwards the {@code Authorization}
 * header exactly as the client sent it and leaves every token decision — issuing,
 * validating, rejecting — to the services behind it. This class proves the three properties
 * that follow from that design, against a mock downstream that echoes the header it
 * received:</p>
 *
 * <ul>
 *   <li>a bearer token reaches the target service byte-for-byte, on every route;</li>
 *   <li>a request with no credential arrives with no credential — the gateway invents
 *       nothing;</li>
 *   <li>a garbage or expired token is <em>not</em> rejected at the edge: it is forwarded,
 *       and the downstream 401 (error contract plus {@code invalid_token} challenge) is
 *       returned to the client untouched. Token validation is therefore never duplicated
 *       in the gateway.</li>
 * </ul>
 *
 * <p>No signing key or JWT library exists in this module on purpose — the gateway has no
 * secret to validate a signature with, and duplicating validation would let the edge and a
 * service disagree about who is authenticated.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayAuthenticationTests {

    /** A syntactically well-formed but unsigned fixture token; never a production key. */
    private static final String BEARER_TOKEN =
            "unit-test-only-signing-key-do-not-use-in-production-0001";

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
    void forwardsAuthorizationHeaderVerbatimOnEveryRoute() {
        String authorization = "Bearer " + BEARER_TOKEN;

        assertForwardedAuthorization("/api/users/1", authorization);
        assertForwardedAuthorization("/api/candidates/1", authorization);
        assertForwardedAuthorization("/api/jobs/1", authorization);
        assertForwardedAuthorization("/api/applications/1", authorization);
    }

    @Test
    void forwardsRequestWithoutCredentialsUnchanged() {
        client().get().uri("/api/users/1").exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("|authorization=absent"));
    }

    @Test
    void doesNotValidateTokensItself() {
        // A token the gateway cannot possibly verify is forwarded, not rejected: an
        // "expired" marker, a non-JWT string and an empty scheme all reach the service,
        // which is the only component allowed to decide whether they are valid.
        for (String malformed : new String[] {
                "Bearer expired-unit-test-token",
                "Bearer not-a-jwt-at-all",
                "Basic dW5pdC10ZXN0LW9ubHk=",
                "Bearer"}) {
            client().get().uri("/api/users/1")
                    .header("Authorization", malformed)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .value(body -> assertThat(body)
                            .as("token must be forwarded, not rejected at the edge")
                            .contains("|authorization=" + malformed));
        }
    }

    @Test
    void passesDownstreamInvalidTokenRejectionThroughUntouched() {
        // The downstream rejects the token; the gateway must relay that 401 — status,
        // error contract and invalid_token challenge — without adding its own verdict.
        client().get().uri("/api/users/security/401")
                .header("Authorization", "Bearer expired-unit-test-token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate",
                        "Bearer realm=\"Mock auth-service\", error=\"invalid_token\"")
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("\"code\":\"UNAUTHENTICATED\"")
                        .contains("\"message\":\"Invalid or expired authentication token\"")
                        .doesNotContain("gateway"));
    }

    private void assertForwardedAuthorization(String path, String authorization) {
        client().get().uri(path)
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .as("Authorization header must reach %s unchanged", path)
                        .contains("|authorization=" + authorization));
    }
}
