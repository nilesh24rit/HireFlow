package com.hireflow.job;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.hireflow.job.security.TestSigningKeys;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * The token contract job-service expects from auth-service.
 *
 * <p>{@code EndpointSecurityIntegrationTest} proves that endpoints are protected and that
 * expired, foreign-key and tampered tokens fail. This class pins the contract between the
 * issuer and this validator from the validator's side, over the real filter chain:</p>
 *
 * <ul>
 *   <li>a token carrying the exact claim set auth-service issues — {@code sub}, {@code iss},
 *       {@code iat}, {@code exp} and the server-derived {@code role} — is accepted, so a
 *       token fresh from {@code POST /api/auth/login} will open this service;</li>
 *   <li>tokens auth-service would never issue — a foreign issuer, {@code alg=none}, or
 *       HS384 instead of HS256 — are rejected with the common HireFlow 401 contract and the
 *       {@code invalid_token} challenge, and the response never says which check failed.</li>
 * </ul>
 *
 * <p>All signing keys are the dedicated test-only keys of {@link TestSigningKeys}.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class TokenContractIntegrationTest {

    private static final String DATABASE_NAME = "hireflow_job_token_contract";

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
    void tokenWithTheCompleteAuthServiceClaimSetIsAccepted() throws Exception {
        // Exactly what POST /api/auth/login returns: sub, iss, iat, exp, role — HS256,
        // signed with the shared key. Reaching the handler (and its 404 for an id that
        // does not exist) proves authentication succeeded.
        String token = signedToken(TestSigningKeys.VALID, UUID.randomUUID(),
                "hireflow-auth", JWSAlgorithm.HS256, "RECRUITER",
                Date.from(Instant.now().plusSeconds(3600)));

        mockMvc.perform(get("/api/jobs/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() throws Exception {
        String foreignIssuer = signedToken(TestSigningKeys.VALID, UUID.randomUUID(),
                "someone-else.example", JWSAlgorithm.HS256, "CANDIDATE",
                Date.from(Instant.now().plusSeconds(3600)));

        assertRejected(foreignIssuer);
    }

    @Test
    void unsignedAlgNoneTokenIsRejected() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer("hireflow-auth")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .claim("role", "CANDIDATE")
                .build();

        assertRejected(new PlainJWT(claims).serialize());
    }

    @Test
    void tokenSignedWithAnotherAlgorithmIsRejected() throws Exception {
        String hs384 = signedToken(TestSigningKeys.VALID, UUID.randomUUID(),
                "hireflow-auth", JWSAlgorithm.HS384, "CANDIDATE",
                Date.from(Instant.now().plusSeconds(3600)));

        assertRejected(hs384);
    }

    @Test
    void rejectionNeverRevealsWhichCheckFailed() throws Exception {
        String foreignIssuer = signedToken(TestSigningKeys.VALID, UUID.randomUUID(),
                "someone-else.example", JWSAlgorithm.HS256, "CANDIDATE",
                Date.from(Instant.now().plusSeconds(3600)));

        mockMvc.perform(get("/api/jobs/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + foreignIssuer))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Invalid or expired authentication token"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private void assertRejected(String token) throws Exception {
        mockMvc.perform(get("/api/jobs/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate",
                        "Bearer realm=\"HireFlow job-service\", error=\"invalid_token\""))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Invalid or expired authentication token"));
    }

    /** Signs a token the way auth-service does; algorithm, issuer and role are inputs. */
    private String signedToken(String signingKey, UUID subject, String issuer,
            JWSAlgorithm algorithm, String role, Date expiresAt) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject.toString())
                .issuer(issuer)
                .issueTime(new Date())
                .expirationTime(expiresAt)
                .claim("role", role)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(algorithm), claims);
        jwt.sign(new MACSigner(signingKey.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
