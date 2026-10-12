package com.hireflow.candidate.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The role claim is <b>required</b> by candidate-service's validator.
 *
 * <p>Every token auth-service issues carries the server-derived {@code role} claim, so a
 * token without it (or with a value HireFlow never issues) is not a usable HireFlow token.
 * It is rejected outright rather than authenticated with an empty or guessed role —
 * authorization never runs on a principal whose role is unknown.</p>
 *
 * <p>Signing keys are the dedicated test-only keys of {@link TestSigningKeys}.</p>
 */
class JwtServiceRoleClaimTest {

    private static final String ISSUER = "hireflow-auth";

    private final JwtService jwtService = new JwtService(TestSigningKeys.VALID, ISSUER);

    @Test
    void tokenWithAKnownRoleClaimAuthenticatesWithThatRole() throws Exception {
        UUID userId = UUID.randomUUID();

        AuthenticatedUser candidate = jwtService.authenticate(
                signedToken(userId, "CANDIDATE"));
        AuthenticatedUser recruiter = jwtService.authenticate(
                signedToken(userId, "RECRUITER"));
        AuthenticatedUser admin = jwtService.authenticate(
                signedToken(userId, "ADMIN"));

        assertThat(candidate).isEqualTo(new AuthenticatedUser(userId, UserRole.CANDIDATE));
        assertThat(recruiter).isEqualTo(new AuthenticatedUser(userId, UserRole.RECRUITER));
        assertThat(admin).isEqualTo(new AuthenticatedUser(userId, UserRole.ADMIN));
    }

    @Test
    void tokenMissingTheRoleClaimIsRejected() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .build();
        SignedJWT roleless = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        roleless.sign(new MACSigner(TestSigningKeys.VALID.getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> jwtService.authenticate(roleless.serialize()))
                .isInstanceOf(InvalidTokenException.class)
                .satisfies(ex -> assertThat(((InvalidTokenException) ex).reason())
                        .isEqualTo(InvalidTokenException.Reason.MISSING_CLAIMS));
    }

    @Test
    void tokenWithAnUnknownRoleClaimIsRejected() throws Exception {
        assertThatThrownBy(() -> jwtService.authenticate(signedToken(UUID.randomUUID(), "SUPERUSER")))
                .isInstanceOf(InvalidTokenException.class)
                .satisfies(ex -> assertThat(((InvalidTokenException) ex).reason())
                        .isEqualTo(InvalidTokenException.Reason.MISSING_CLAIMS));
    }

    /** Signs a token the way auth-service does, with the role claim as an input. */
    private String signedToken(UUID subject, String role) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject.toString())
                .issuer(ISSUER)
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .claim("role", role)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(TestSigningKeys.VALID.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
