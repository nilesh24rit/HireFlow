package com.hireflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.hireflow.auth.entity.User;
import com.hireflow.auth.entity.UserRole;
import com.hireflow.auth.repository.UserRepository;
import com.hireflow.auth.security.TestSigningKeys;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * The issued-token contract observed over real HTTP.
 *
 * <p>{@code AuthSecurityBaselineTest} proves that a login token opens protected endpoints
 * and that garbage, tampered and expired tokens do not. This class pins down the token
 * itself and the rejection classes that depend on the issuer and the algorithm:</p>
 *
 * <ul>
 *   <li>the access token returned by {@code POST /api/auth/login} carries exactly the
 *       designed claims — subject (the user's UUID), issuer, issue and expiry times, and
 *       the server-derived role — with a lifetime equal to the configured TTL;</li>
 *   <li>a token with a foreign issuer, an {@code alg=none} token, and a token signed with
 *       HS384 instead of HS256 are all rejected at the edge of the filter chain with the
 *       common HireFlow 401 contract and the {@code invalid_token} challenge, never with a 500 and
 *       never with a message that explains which check failed.</li>
 * </ul>
 *
 * <p>All signing keys are the dedicated test-only keys of {@link TestSigningKeys}.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JwtIssuedTokenIntegrationTest {

    private static final String DATABASE_NAME = "hireflow_auth_jwt_flow";
    private static final String RAW_PASSWORD = "Sup3r-Secret!";
    private static final long EXPECTED_TTL_SECONDS = 3600;

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

    // --------------------------------------------------
    // The token a client actually receives
    // --------------------------------------------------

    @Test
    void loginTokenCarriesExactlyTheDesignedClaims() throws Exception {
        User user = seedUser(UserRole.RECRUITER);

        String token = loginAndGetToken(user.getEmail(), RAW_PASSWORD);
        SignedJWT jwt = SignedJWT.parse(token);
        JWTClaimsSet claims = jwt.getJWTClaimsSet();

        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
        assertThat(claims.getClaims().keySet())
                .as("the token stays minimal: identity, validity window, issuer, role")
                .containsExactlyInAnyOrder("sub", "iss", "iat", "exp", "role");
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.getIssuer()).isEqualTo("hireflow-auth");
        assertThat(claims.getStringClaim("role")).isEqualTo("RECRUITER");

        Instant issuedAt = claims.getIssueTime().toInstant();
        Instant expiresAt = claims.getExpirationTime().toInstant();
        assertThat(expiresAt.getEpochSecond() - issuedAt.getEpochSecond())
                .isEqualTo(EXPECTED_TTL_SECONDS);
        assertThat(issuedAt).isBeforeOrEqualTo(Instant.now().plusSeconds(5));
    }

    @Test
    void loginTokenOpensProtectedEndpointsOverRealHttp() throws Exception {
        User user = seedUser(UserRole.CANDIDATE);

        String token = loginAndGetToken(user.getEmail(), RAW_PASSWORD);
        HttpResponse<String> response = get("/api/users/" + user.getId(), "Bearer " + token);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(user.getEmail());
        // Stateless: accepting a token never sets a cookie.
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
    }

    // --------------------------------------------------
    // Rejections that only an integration test can observe
    // --------------------------------------------------

    @Test
    void tokenFromAnotherIssuerIsRejectedWithInvalidTokenChallenge() throws Exception {
        String foreignIssuer = tokenSignedWith(TestSigningKeys.VALID, UUID.randomUUID(),
                "someone-else.example", JWSAlgorithm.HS256,
                Date.from(Instant.now().plusSeconds(3600)));

        assertInvalidToken401("/api/users/" + UUID.randomUUID(), "Bearer " + foreignIssuer);
    }

    @Test
    void unsignedAlgNoneTokenIsRejectedWithInvalidTokenChallenge() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer("hireflow-auth")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .build();
        String algNone = new PlainJWT(claims).serialize();

        assertInvalidToken401("/api/users/" + UUID.randomUUID(), "Bearer " + algNone);
    }

    @Test
    void tokenSignedWithAnotherAlgorithmIsRejectedWithInvalidTokenChallenge() throws Exception {
        String hs384 = tokenSignedWith(TestSigningKeys.VALID, UUID.randomUUID(),
                "hireflow-auth", JWSAlgorithm.HS384,
                Date.from(Instant.now().plusSeconds(3600)));

        assertInvalidToken401("/api/users/" + UUID.randomUUID(), "Bearer " + hs384);
    }

    @Test
    void rejectedTokensNeverLeakValidationDetails() throws Exception {
        String foreignIssuer = tokenSignedWith(TestSigningKeys.VALID, UUID.randomUUID(),
                "someone-else.example", JWSAlgorithm.HS256,
                Date.from(Instant.now().plusSeconds(3600)));

        HttpResponse<String> response = get("/api/users/" + UUID.randomUUID(),
                "Bearer " + foreignIssuer);

        assertThat(response.body())
                .doesNotContain("someone-else.example")
                .doesNotContain("issuer")
                .doesNotContain("signature")
                .doesNotContain("HS256")
                .doesNotContain(TestSigningKeys.VALID);
    }

    // --------------------------------------------------
    // Helpers
    // --------------------------------------------------

    private void assertInvalidToken401(String path, String authorization) throws Exception {
        HttpResponse<String> response = get(path, authorization);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate"))
                .hasValue("Bearer realm=\"HireFlow auth-service\", error=\"invalid_token\"");
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
        assertThat(response.body())
                .contains("\"code\":\"UNAUTHENTICATED\"")
                .contains("\"message\":\"Invalid or expired authentication token\"")
                .contains("\"path\":\"" + path + "\"")
                .doesNotContain("Exception")
                .doesNotContain("trace");
    }

    /** Signs a token the way auth-service does, with the issuer and algorithm as inputs. */
    private String tokenSignedWith(String key, UUID subject, String issuer,
            JWSAlgorithm algorithm, Date expiresAt) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject.toString())
                .issuer(issuer)
                .issueTime(new Date())
                .expirationTime(expiresAt)
                .claim("role", "CANDIDATE")
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(algorithm), claims);
        jwt.sign(new MACSigner(key.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
        HttpResponse<String> response = post("/api/auth/login", body, null);
        assertThat(response.statusCode()).isEqualTo(200);
        return readJsonString(response.body(), "accessToken");
    }

    private HttpResponse<String> get(String path, String authorization) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + path)).GET();
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return send(builder.build());
    }

    private HttpResponse<String> post(String path, String body, String authorization)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + path))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return send(builder.build());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return HttpClient.newHttpClient().send(request,
                HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String readJsonString(String body, String field) {
        String marker = "\"" + field + "\":\"";
        int start = body.indexOf(marker) + marker.length();
        return body.substring(start, body.indexOf('"', start));
    }

    private User seedUser(UserRole role) {
        User user = new User();
        user.setEmail("jwt-flow-" + UUID.randomUUID() + "@example.com");
        user.setFirstName("Jwt");
        user.setLastName("Flow");
        user.setRole(role);
        user.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        return userRepository.saveAndFlush(user);
    }
}
