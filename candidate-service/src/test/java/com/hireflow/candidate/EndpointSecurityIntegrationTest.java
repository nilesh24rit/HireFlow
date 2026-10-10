package com.hireflow.candidate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.hireflow.candidate.security.TestSigningKeys;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Endpoint protection for candidate-service (Step 13, checkpoint "protect service endpoints").
 *
 * <p>Before Step 13 this service had no security stack â€” every endpoint was reachable
 * anonymously. These tests observe the protected behaviour through the real filter chain:
 * business endpoints under {@code /api/candidates} reject requests without a valid bearer token
 * using the shared Step 9 error contract, accept properly signed tokens (no CSRF token
 * required â€” the API is stateless), reject foreign, expired and tampered tokens, and the
 * API documentation stays public by deliberate policy.
 *
 * <p>All signing keys are the dedicated test-only keys of {@link TestSigningKeys}.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class EndpointSecurityIntegrationTest {

    private static final String DATABASE_NAME = "hireflow_candidate_security";

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

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void getWithoutTokenIsRejectedWith401UsingErrorContract() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/candidates/{id}", id))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"HireFlow candidate-service\""))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.path").value("/api/candidates/" + id))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void postWithoutTokenIsRejectedBeforeAnyValidation() throws Exception {
        // Security first: an anonymous write is a 401, never a 400 about its payload.
        mockMvc.perform(post("/api/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void validSignedTokenPassesAuthentication() throws Exception {
        // A token signed exactly as auth-service signs it authenticates; the unknown id
        // then fails with 404, proving the request reached the handler behind security.
        String token = tokenSignedWith(TestSigningKeys.VALID, UUID.randomUUID().toString(),
                Date.from(Instant.now().plusSeconds(3600)));

        mockMvc.perform(get("/api/candidates/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void authenticatedWriteNeedsNoCsrfToken() throws Exception {
        // CSRF is deliberately disabled for this stateless API: a bearer-authenticated
        // POST without any CSRF token must reach validation, not be rejected with 401/403.
        String token = tokenSignedWith(TestSigningKeys.VALID, UUID.randomUUID().toString(),
                Date.from(Instant.now().plusSeconds(3600)));

        mockMvc.perform(post("/api/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidBearerTokenGetsInvalidTokenChallenge() throws Exception {
        mockMvc.perform(get("/api/candidates/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer definitely-not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate",
                        "Bearer realm=\"HireFlow candidate-service\", error=\"invalid_token\""))
                .andExpect(jsonPath("$.message").value("Invalid or expired authentication token"));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        String foreignToken = tokenSignedWith(TestSigningKeys.OTHER, UUID.randomUUID().toString(),
                Date.from(Instant.now().plusSeconds(3600)));

        mockMvc.perform(get("/api/candidates/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String expiredToken = tokenSignedWith(TestSigningKeys.VALID, UUID.randomUUID().toString(),
                Date.from(Instant.now().minusSeconds(60)));

        mockMvc.perform(get("/api/candidates/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = tokenSignedWith(TestSigningKeys.VALID, UUID.randomUUID().toString(),
                Date.from(Instant.now().plusSeconds(3600)));
        // Flip a character in the MIDDLE of the signature. The final base64url character
        // of a 32-byte HMAC carries padding bits after its 4 data bits, so changing only
        // that character can decode to the identical byte array and leave the token valid.
        int lastDot = token.lastIndexOf('.');
        String signature = token.substring(lastDot + 1);
        int middle = signature.length() / 2;
        char original = signature.charAt(middle);
        String tampered = token.substring(0, lastDot + 1)
                + signature.substring(0, middle) + (original == 'A' ? 'B' : 'A')
                + signature.substring(middle + 1);

        mockMvc.perform(get("/api/candidates/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerApiDocsStayPublicByDeliberatePolicy() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());
    }

    /** Mirrors how auth-service signs tokens, with a configurable key and expiry. */
    private String tokenSignedWith(String signingKey, String subject, Date expiresAt) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer("hireflow-auth")
                .issueTime(new Date())
                .expirationTime(expiresAt)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(signingKey.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
