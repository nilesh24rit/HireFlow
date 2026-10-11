package com.hireflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Routing of the three paths Google sign-in needs, against a mock auth-service.
 *
 * <p>A browser OAuth2 flow only works if the entry point that redirects to Google, the
 * callback Google redirects back to, and the endpoint that redeems the resulting handoff code
 * are all reachable on the public origin the user is looking at. This class proves the
 * gateway forwards exactly those paths — with their query parameters, bodies and redirects
 * intact — and nothing else about authentication moves to the edge:</p>
 *
 * <ul>
 *   <li>the entry point's 302 to Google, including the public callback in
 *       {@code redirect_uri}, is relayed untouched;</li>
 *   <li>the callback's {@code code} and {@code state} reach auth-service unchanged, since
 *       Spring Security needs both to finish the flow and to defeat login CSRF;</li>
 *   <li>the handoff exchange keeps its JSON body, so the one-time code arrives as sent;</li>
 *   <li>the four existing resource routes, and every unlisted path, behave exactly as
 *       before — credential login in particular remains direct-to-service;</li>
 *   <li>no forwarded-header trust is introduced: the gateway adds none of its own and
 *       strips any a caller sent, so nothing at the edge can describe the public origin —
 *       which is exactly why auth-service pins its OAuth2 callback URL through operator
 *       configuration instead.</li>
 * </ul>
 *
 * <p>No real Google account, credential or network call is involved: the downstream is the
 * in-JVM {@link MockDownstream}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayOAuth2RoutingTests {

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
    void theEntryPointIsRoutedToAuthServiceAsAnUnchangedRedirectToGoogle() {
        client().get().uri("/oauth2/authorization/google").exchange()
                .expectStatus().isFound()
                .expectHeader().value("Location", location -> assertThat(location)
                        .startsWith("https://accounts.google.com/o/oauth2/v2/auth")
                        // The callback the browser will be sent back to names the PUBLIC
                        // origin the user is looking at, not auth-service's internal port.
                        .contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Flogin%2Foauth2%2Fcode%2Fgoogle")
                        .contains("state=unit-test-state"));
    }

    @Test
    void theCallbackReachesAuthServiceWithItsCodeAndStateIntact() {
        client().get().uri(uriBuilder -> uriBuilder
                        .path("/login/oauth2/code/google")
                        .queryParam("code", "google-authorization-code")
                        .queryParam("state", "unit-test-state")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("service=auth-service")
                        .contains("path=/login/oauth2/code/google")
                        // Both parameters must survive: Spring Security validates the state
                        // and exchanges the code in auth-service, not here.
                        .contains("query=code=google-authorization-code&state=unit-test-state"));
    }

    @Test
    void theHandoffExchangeReachesAuthServiceWithItsBodyIntact() {
        String handoff = "{\"handoffCode\":\"one-time-code\"}";

        client().post().uri("/api/auth/oauth2/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(handoff)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("service=auth-service")
                        .contains("method=POST")
                        .contains("path=/api/auth/oauth2/token")
                        .contains("body=" + handoff));
    }

    @Test
    void aForwardedHeaderSuppliedByAClientIsStrippedRatherThanForwarded() {
        // The gateway removes caller-supplied X-Forwarded-* and Forwarded headers instead
        // of relaying them, so no downstream can be talked into believing a caller's claim
        // about the public origin. Together with the gateway adding none of its own (next
        // test), no forwarded header anywhere in the Google sign-in flow can describe the
        // callback URL — which is exactly why auth-service pins that URL through operator
        // configuration (HIREFLOW_OAUTH_REDIRECT_URI) rather than reading a header
        // (asserted on the auth-service side by GoogleOAuth2RedirectUriConfigurationTest).
        client().get().uri("/api/users/1")
                .header("X-Forwarded-Host", "attacker.example")
                .header("X-Forwarded-Proto", "https")
                .header("Forwarded", "host=attacker.example;proto=https")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .as("a forged forwarded header must never reach a service")
                        .contains("X-Forwarded-Host=absent")
                        .contains("X-Forwarded-Proto=absent")
                        .contains("Forwarded=absent")
                        .doesNotContain("attacker.example"));
    }

    @Test
    void theGatewaySuppliesNoForwardedHeaderOfItsOwn() {
        // Nothing the edge says about the public origin is needed for Google sign-in, and
        // nothing it says can be mistaken for one: with no forwarded headers added, the
        // only statement about the callback URL anywhere in the flow is the operator's
        // pinned value. This pins the current behaviour so enabling forwarded-header
        // handling later is a deliberate, visible decision rather than an accident.
        client().get().uri("/api/users/1").exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("X-Forwarded-Host=absent")
                        .contains("X-Forwarded-Proto=absent")
                        .contains("Forwarded=absent"));
    }

    @Test
    void everyExistingRouteIsUnchangedByTheOAuth2Routes() {
        assertRoutesTo("/api/users/42", "auth-service");
        assertRoutesTo("/api/candidates/7", "candidate-service");
        assertRoutesTo("/api/jobs/9", "job-service");
        assertRoutesTo("/api/applications/3", "application-service");
    }

    @Test
    void pathsThatAreNotOAuth2StayUnrouted() {
        // Only the three OAuth2 paths were added. Credential login keeps its existing
        // direct-to-service deployment and is still not proxied.
        client().post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isNotFound();

        client().get().uri("/api/unknown/thing").exchange().expectStatus().isNotFound();
        client().get().uri("/not-routed").exchange().expectStatus().isNotFound();
    }

    private void assertRoutesTo(String path, String expectedService) {
        client().get().uri(path).exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .as("%s must still route to %s", path, expectedService)
                        .contains("service=" + expectedService)
                        .contains("path=" + path));
    }
}
