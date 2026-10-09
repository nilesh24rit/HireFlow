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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * STEP 12 inspection record: the ACTUAL security behaviour of auth-service, captured
 * before any security configuration is introduced.
 *
 * <p>Audit findings encoded by these tests:
 *
 * <ul>
 *   <li><b>Dependencies:</b> auth-service is the only service carrying
 *       {@code spring-boot-starter-security}. The other seven services have no security
 *       dependency at all.</li>
 *   <li><b>Configuration:</b> no {@code SecurityFilterChain} exists anywhere in the
 *       repository, so Spring Boot's default auto-configuration applies: every request
 *       requires authentication, HTTP Basic <i>and</i> a generated HTML form login page
 *       are enabled, sessions are created, and CSRF protection is on.</li>
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
 *   <li><b>Proof:</b> the same POST carrying valid Basic credentials <i>and</i> a CSRF
 *       token taken from the generated login page succeeds with 201, so authentication
 *       works and the absent CSRF token is the sole blocker.</li>
 *   <li><b>Swagger:</b> {@code /v3/api-docs} requires authentication (401 anonymous,
 *       200 authenticated) — the policy already asserted by {@code OpenApiDocumentationTest}.</li>
 *   <li><b>Security responses:</b> 401 responses carry no body (the Step 9 error contract
 *       is not applied to security rejections yet).</li>
 * </ul>
 *
 * <p>These tests run against a real HTTP server instead of MockMvc because the 401
 * masking only happens through the servlet container's error dispatch.
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

    private static final Pattern CSRF_TOKEN_INPUT =
            Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

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
    void getWithoutCredentialsIsRejectedWith401AndEmptyBody() throws Exception {
        User user = seedUser();

        HttpResponse<String> response = get("/api/users/" + user.getId(), null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate"))
                .hasValueSatisfying(challenge -> assertThat(challenge).startsWith("Basic"));
        assertThat(response.body()).isEmpty();
    }

    @Test
    void getWithBasicCredentialsReturnsUser() throws Exception {
        User user = seedUser();

        HttpResponse<String> response = get("/api/users/" + user.getId(), basicAuthorization);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(user.getEmail());
    }

    @Test
    void postWithBasicCredentialsButNoCsrfTokenIsRejectedWith401() throws Exception {
        // Records the actual (misleading) behaviour: authentication succeeds conceptually,
        // CsrfFilter rejects with 403, and the protected /error dispatch rewrites it to 401.
        HttpResponse<String> response = post("/api/users", createUserBody(), basicAuthorization, null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate"))
                .hasValueSatisfying(challenge -> assertThat(challenge).startsWith("Basic"));
        assertThat(response.body()).isEmpty();
    }

    @Test
    void postWithBasicCredentialsAndCsrfTokenFromGeneratedLoginPageIsAccepted() throws Exception {
        // Proof that the 401 above is a masked CSRF rejection: adding a valid CSRF token
        // makes the very same authenticated POST succeed.
        HttpClient client = httpClient();

        HttpResponse<String> loginPage = send(client, request("/login").GET().build());
        assertThat(loginPage.statusCode()).isEqualTo(200);
        String csrfToken = extractCsrfToken(loginPage.body());

        HttpResponse<String> response = send(client, request("/api/users")
                .header("Authorization", basicAuthorization)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createUserBody(), StandardCharsets.UTF_8))
                .build());

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Location")).isPresent();
    }

    @Test
    void swaggerApiDocsRequireAuthentication() throws Exception {
        assertThat(get("/v3/api-docs", null).statusCode()).isEqualTo(401);

        HttpResponse<String> authenticated = get("/v3/api-docs", basicAuthorization);
        assertThat(authenticated.statusCode()).isEqualTo(200);
        assertThat(authenticated.body()).contains("openapi");
    }

    @Test
    void defaultFormLoginPageIsServedAtLoginPath() throws Exception {
        // Records that Spring Boot's default auto-configuration currently exposes a
        // session-based HTML form login page, which no HireFlow client uses.
        HttpResponse<String> response = get("/login", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("username");
    }

    private HttpClient httpClient() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
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

    private String extractCsrfToken(String loginPageHtml) {
        Matcher matcher = CSRF_TOKEN_INPUT.matcher(loginPageHtml);
        assertThat(matcher.find())
                .as("generated login page must expose a CSRF token")
                .isTrue();
        return matcher.group(1);
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
