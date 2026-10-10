package com.hireflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.hireflow.auth.entity.User;
import com.hireflow.auth.entity.UserRole;
import com.hireflow.auth.repository.UserRepository;
import com.hireflow.auth.security.JwtService;
import com.hireflow.auth.security.TestSigningKeys;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Records the ACTUAL security behaviour of auth-service through a real HTTP server
 * instead of MockMvc, because the servlet container's error dispatch is part of the
 * observed behaviour and cannot be reproduced with MockMvc.
 *
 * <p>Since Step 13 the mechanism is stateless bearer JWT (HTTP Basic was the interim
 * mechanism of Step 12 and is gone). This baseline observes over real HTTP that:</p>
 *
 * <ul>
 *   <li>the login endpoint is the only public route and issues verifiable tokens;</li>
 *   <li>protected routes accept a valid {@code Authorization: Bearer <token>} and reject
 *       everything else — no token, garbage, tampered, expired, or signed with another
 *       key — with the Step 9 401 contract, the {@code Bearer} challenge and, for failed
 *       tokens, {@code error="invalid_token"};</li>
 *   <li>rejected requests keep the ORIGINAL request path (no {@code /error} dispatch
 *       rewrites it), no session cookie is ever issued, and no form login page exists;</li>
 *   <li>Swagger stays behind authentication, the deliberate policy for this service.</li>
 * </ul>
 *
 * <p>All signing keys are the dedicated test-only keys of {@link TestSigningKeys}.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthSecurityBaselineTest {

    private static final String DATABASE_NAME = "hireflow_auth";
    private static final String RAW_PASSWORD = "Sup3r-Secret!";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName(DATABASE_NAME);

    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Test-only JWT signing key; JwtService refuses to start without one.
        registry.add("hireflow.jwt.signing-key", () -> TestSigningKeys.VALID);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Test
    void getWithoutTokenIsRejectedWith401UsingErrorContract() throws Exception {
        User user = seedUser();

        HttpResponse<String> response = get("/api/users/" + user.getId(), null);

        assertUnauthenticatedErrorContract(response, "/api/users/" + user.getId(),
                "Authentication required", "Bearer realm=\"HireFlow auth-service\"");
    }

    @Test
    void loginThenBearerTokenOpensProtectedEndpoint() throws Exception {
        User user = seedUser();

        HttpResponse<String> login = post("/api/auth/login",
                loginBody(user.getEmail(), RAW_PASSWORD), null);
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).contains("\"tokenType\":\"Bearer\"");

        String accessToken = readJsonString(login.body(), "accessToken");
        HttpResponse<String> protectedCall = get("/api/users/" + user.getId(),
                "Bearer " + accessToken);

        assertThat(protectedCall.statusCode()).isEqualTo(200);
        assertThat(protectedCall.body()).contains(user.getEmail());
    }

    @Test
    void invalidTokenIsRejectedWith401AndInvalidTokenChallenge() throws Exception {
        String path = "/api/users/" + UUID.randomUUID();

        HttpResponse<String> response = get(path, "Bearer definitely-not-a-jwt");

        assertUnauthenticatedErrorContract(response, path,
                "Invalid or expired authentication token",
                "Bearer realm=\"HireFlow auth-service\", error=\"invalid_token\"");
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), UserRole.CANDIDATE).token();
        int lastDot = token.lastIndexOf('.');
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, lastDot + 1)
                + (last == 'A' ? 'B' : 'A') + token.substring(lastDot + 2);

        HttpResponse<String> response = get("/api/users/" + UUID.randomUUID(), "Bearer " + tampered);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        JwtService foreignDeployment = new JwtService(TestSigningKeys.OTHER, 3600, "hireflow-auth");
        String foreignToken = foreignDeployment.generateAccessToken(UUID.randomUUID(), UserRole.CANDIDATE).token();

        HttpResponse<String> response = get("/api/users/" + UUID.randomUUID(), "Bearer " + foreignToken);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer("hireflow-auth")
                .issueTime(Date.from(Instant.now().minusSeconds(120)))
                .expirationTime(Date.from(Instant.now().minusSeconds(60)))
                .build();
        SignedJWT expired = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        expired.sign(new MACSigner(TestSigningKeys.VALID.getBytes(StandardCharsets.UTF_8)));

        HttpResponse<String> response = get("/api/users/" + UUID.randomUUID(),
                "Bearer " + expired.serialize());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void postWithBearerTokenIsAcceptedWithoutCsrfTokenAndIssuesNoCookie() throws Exception {
        User user = seedUser();
        String token = jwtService.generateAccessToken(user.getId(), user.getRole()).token();

        HttpResponse<String> response = post("/api/users", createUserBody(),
                "Bearer " + token);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Location")).isPresent();
        // Stateless: no session cookie may be issued for the request.
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
    }

    @Test
    void postWithoutTokenIsStillRejectedWith401() throws Exception {
        HttpResponse<String> response = post("/api/users", createUserBody(), null);

        assertUnauthenticatedErrorContract(response, "/api/users",
                "Authentication required", "Bearer realm=\"HireFlow auth-service\"");
    }

    @Test
    void swaggerApiDocsRequireBearerToken() throws Exception {
        assertThat(get("/v3/api-docs", null).statusCode()).isEqualTo(401);

        User user = seedUser();
        String token = jwtService.generateAccessToken(user.getId(), user.getRole()).token();
        HttpResponse<String> authenticated = get("/v3/api-docs", "Bearer " + token);
        assertThat(authenticated.statusCode()).isEqualTo(200);
        assertThat(authenticated.body()).contains("openapi");
    }

    @Test
    void generatedFormLoginPageIsNoLongerExposed() throws Exception {
        // No form login exists; /login is an ordinary protected path answering 401
        // with the Bearer challenge.
        HttpResponse<String> response = get("/login", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate"))
                .hasValueSatisfying(challenge -> assertThat(challenge).startsWith("Bearer"));
    }

    private HttpClient httpClient() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Asserts the deliberate 401 shape: Step 9 error contract naming the original request
     * path, the expected challenge, and no stack traces, exception names or internals.
     */
    private void assertUnauthenticatedErrorContract(HttpResponse<String> response, String expectedPath,
            String expectedMessage, String expectedChallenge) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).hasValue(expectedChallenge);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
        assertThat(response.body())
                .contains("\"timestamp\":")
                .contains("\"status\":401")
                .contains("\"error\":\"Unauthorized\"")
                .contains("\"code\":\"UNAUTHENTICATED\"")
                .contains("\"message\":\"" + expectedMessage + "\"")
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
        return send(builder.build());
    }

    private HttpResponse<String> post(String path, String body, String authorization) throws Exception {
        HttpRequest.Builder builder = request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return send(builder.build());
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return httpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String readJsonString(String body, String field) {
        String marker = "\"" + field + "\":\"";
        int start = body.indexOf(marker) + marker.length();
        return body.substring(start, body.indexOf('"', start));
    }

    private User seedUser() {
        User user = new User();
        user.setEmail("baseline-" + UUID.randomUUID() + "@example.com");
        user.setFirstName("Baseline");
        user.setLastName("User");
        user.setRole(UserRole.CANDIDATE);
        user.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        return userRepository.saveAndFlush(user);
    }

    private String loginBody(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private String createUserBody() {
        return """
                {"email":"baseline-create-%s@example.com","firstName":"Baseline","lastName":"Create","password":"Sup3r-Secret!","role":"CANDIDATE"}
                """.formatted(UUID.randomUUID());
    }
}
