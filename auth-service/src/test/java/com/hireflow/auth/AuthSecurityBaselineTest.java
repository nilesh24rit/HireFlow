package com.hireflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.hireflow.auth.entity.User;
import com.hireflow.auth.entity.UserRole;
import com.hireflow.auth.repository.UserRepository;

/**
 * Records the ACTUAL security behaviour of auth-service through a real HTTP server
 * instead of MockMvc, because the servlet container's error dispatch is part of the
 * observed behaviour and cannot be reproduced with MockMvc.
 *
 * <p>Inspection findings (checkpoint: "inspect existing security"):
 *
 * <ul>
 *   <li><b>Dependencies:</b> auth-service is the only service carrying
 *       {@code spring-boot-starter-security}. The other seven services have no security
 *       dependency at all.</li>
 *   <li><b>Configuration as found:</b> no {@code SecurityFilterChain} existed anywhere in
 *       the repository, so Spring Boot's default auto-configuration applied: every
 *       request required authentication while a generated HTML form login page, sessions
 *       and CSRF protection came along as defaults.</li>
 *   <li><b>Why {@code POST /api/users} returned 401:</b> the status is misleading. The
 *       request never fails authentication — it fails CSRF. {@code CsrfFilter} sits
 *       before {@code BasicAuthenticationFilter} in the chain, rejects the token-less
 *       POST with 403 ({@code AccessDeniedHandlerImpl}) and calls {@code sendError(403)}.
 *       The container then forwards the error dispatch to {@code GET /error}, which is
 *       itself covered by {@code anyRequest().authenticated()}; the anonymous error
 *       dispatch is denied and answered by {@code BasicAuthenticationEntryPoint}, whose
 *       401 plus {@code WWW-Authenticate: Basic} overwrites the original 403. The client
 *       therefore observes an empty-body 401 that looks like an authentication failure
 *       but is really a masked CSRF rejection.</li>
 *   <li><b>Proof:</b> at inspection time the same POST carrying valid Basic credentials
 *       <i>and</i> a CSRF token taken from the generated login page succeeded with 201,
 *       proving authentication worked and the absent CSRF token was the sole blocker.
 *       That proof lives in the inspection checkpoint's commit; the login page that
 *       exposed tokens is gone since the explicit chain, so the rejected token-less POST
 *       below is what remains observable.</li>
 *   <li><b>Swagger:</b> {@code /v3/api-docs} requires authentication (401 anonymous,
 *       200 authenticated) — the policy already asserted by {@code OpenApiDocumentationTest}.</li>
 *   <li><b>Security responses:</b> 401 responses originally carried no body; since the
 *       "standardize security responses" checkpoint they deliberately answer in the
 *       Step 9 contract ({@code timestamp, status, error, code, message, path}) with the
 *       Basic challenge kept on 401 and no stack traces or internals anywhere.</li>
 * </ul>
 *
 * <p>Current configuration (checkpoints: "add security foundation", "configure csrf
 * policy"): {@code SecurityConfiguration} declares an explicit {@code SecurityFilterChain} —
 * every request authenticated, HTTP Basic kept, generated form login removed, sessions
 * stateless and CSRF disabled because authority never lives in a cookie or session.
 * {@code GET /login} is an ordinary protected unknown path answering 401, and an
 * authenticated POST needs no CSRF token.
 *
 * <p>Test credentials are local fixtures supplied through test-only properties, the same
 * way the datasource credentials come from the Testcontainers container.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthSecurityBaselineTest {

    private static final String DATABASE_NAME = "hireflow_auth";

    private static final String SECURITY_USER = "baseline-user";
    private static final String SECURITY_PASSWORD = "baseline-password";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName(DATABASE_NAME);

    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.security.user.name", () -> SECURITY_USER);
        registry.add("spring.security.user.password", () -> SECURITY_PASSWORD);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    private String basicAuthorization;

    @BeforeEach
    void setUp() {
        basicAuthorization = "Basic " + Base64.getEncoder()
                .encodeToString((SECURITY_USER + ":" + SECURITY_PASSWORD)
                        .getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void getWithoutCredentialsIsRejectedWith401UsingErrorContract() throws Exception {
        User user = seedUser();

        HttpResponse<String> response = get("/api/users/" + user.getId(), null);

        assertUnauthenticatedErrorContract(response, "/api/users/" + user.getId());
    }

    @Test
    void wrongCredentialsAreRejectedWith401UsingErrorContract() throws Exception {
        // Rejected credentials must produce the same deliberate 401 — and the path of the
        // ORIGINAL request, not the container's /error dispatch path (the configurer's
        // default entry point used to sendError(401), whose error dispatch rewrote it).
        String wrongCredentials = "Basic " + Base64.getEncoder()
                .encodeToString((SECURITY_USER + ":definitely-wrong-password")
                        .getBytes(StandardCharsets.UTF_8));
        String path = "/api/users/" + UUID.randomUUID();

        HttpResponse<String> response = get(path, wrongCredentials);

        assertUnauthenticatedErrorContract(response, path);
    }

    @Test
    void getWithBasicCredentialsReturnsUser() throws Exception {
        User user = seedUser();

        HttpResponse<String> response = get("/api/users/" + user.getId(), basicAuthorization);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(user.getEmail());
    }

    @Test
    void postWithBasicCredentialsIsAcceptedWithoutCsrfToken() throws Exception {
        // CSRF policy (checkpoint: "configure csrf policy"): the stateless, header-authenticated
        // API accepts an authenticated POST without any CSRF token — the request that used to be
        // rejected (403 masked as 401) before the policy was made deliberate.
        HttpResponse<String> response = post("/api/users", createUserBody(), basicAuthorization, null);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Location")).isPresent();
        // Stateless: no session cookie may be issued for the request.
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
    }

    @Test
    void postWithoutCredentialsIsStillRejectedWith401() throws Exception {
        // CSRF is off, but the authentication requirement is untouched: anonymous POSTs
        // remain rejected — now deliberately with the Step 9 error contract body.
        HttpResponse<String> response = post("/api/users", createUserBody(), null, null);

        assertUnauthenticatedErrorContract(response, "/api/users");
    }

    @Test
    void swaggerApiDocsRequireAuthentication() throws Exception {
        assertThat(get("/v3/api-docs", null).statusCode()).isEqualTo(401);

        HttpResponse<String> authenticated = get("/v3/api-docs", basicAuthorization);
        assertThat(authenticated.statusCode()).isEqualTo(200);
        assertThat(authenticated.body()).contains("openapi");
    }

    @Test
    void generatedFormLoginPageIsNoLongerExposed() throws Exception {
        // The explicit SecurityFilterChain no longer registers Spring Boot's default
        // session-based form login page; /login is an ordinary protected path now.
        HttpResponse<String> response = get("/login", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate"))
                .hasValueSatisfying(challenge -> assertThat(challenge).startsWith("Basic"));
    }

    private HttpClient httpClient() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Asserts the deliberate 401 shape: Step 9 error contract naming the original request
     * path, Basic challenge, and no stack traces, exception names or other internals.
     */
    private void assertUnauthenticatedErrorContract(HttpResponse<String> response, String expectedPath) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate"))
                .hasValueSatisfying(challenge -> assertThat(challenge).startsWith("Basic"));
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
        assertThat(response.body())
                .contains("\"timestamp\":")
                .contains("\"status\":401")
                .contains("\"error\":\"Unauthorized\"")
                .contains("\"code\":\"UNAUTHENTICATED\"")
                .contains("\"message\":\"Authentication required\"")
                .contains("\"path\":\"" + expectedPath + "\"")
                .doesNotContain("/error")
                .doesNotContain("Exception")
                .doesNotContain("trace");
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private HttpResponse<String> get(String path, String authorization) throws Exception {
        HttpRequest.Builder builder = request(path).GET();
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return send(httpClient(), builder.build());
    }

    private HttpResponse<String> post(String path, String body, String authorization, String csrfToken)
            throws Exception {
        HttpRequest.Builder builder = request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        if (csrfToken != null) {
            builder.header("X-CSRF-TOKEN", csrfToken);
        }
        return send(httpClient(), builder.build());
    }

    private HttpResponse<String> send(HttpClient client, HttpRequest request)
            throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private User seedUser() {
        User user = new User();
        user.setEmail("baseline-" + UUID.randomUUID() + "@example.com");
        user.setFirstName("Baseline");
        user.setLastName("User");
        user.setRole(UserRole.CANDIDATE);
        return userRepository.saveAndFlush(user);
    }

    private String createUserBody() {
        return """
                {"email":"baseline-create-%s@example.com","firstName":"Baseline","lastName":"Create","role":"CANDIDATE"}
                """.formatted(UUID.randomUUID());
    }
}
