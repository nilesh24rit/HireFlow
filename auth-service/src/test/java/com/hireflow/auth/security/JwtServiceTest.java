package com.hireflow.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hireflow.auth.entity.UserRole;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused unit tests for the JWT token service: claim design, round-trip validation, and rejection of expired, tampered,
 * wrongly signed, wrongly algorithmed and malformed tokens.
 *
 * <p>All keys here are the dedicated test-only keys of {@link TestSigningKeys}; no
 * production credential exists anywhere in the suite.</p>
 */
class JwtServiceTest {

    private static final Duration TEST_TTL = Duration.ofHours(1);
    private static final String TEST_ISSUER = "hireflow-auth";

    private final JwtService jwtService = new JwtService(TestSigningKeys.VALID,
            TEST_TTL.toSeconds(), TEST_ISSUER);

    // --------------------------------------------------
    // Startup configuration safety
    // --------------------------------------------------

    @Test
    void refusesToStartWithoutASigningKey() {
        assertThatThrownBy(() -> new JwtService(null, 3600, TEST_ISSUER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hireflow.jwt.signing-key")
                .hasMessageContaining("HIREFLOW_JWT_SIGNINGKEY");
        assertThatThrownBy(() -> new JwtService("   ", 3600, TEST_ISSUER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hireflow.jwt.signing-key");
    }

    @Test
    void refusesToStartWithAShortSigningKey() {
        assertThatThrownBy(() -> new JwtService(TestSigningKeys.TOO_SHORT, 3600, TEST_ISSUER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void refusesToStartWithANonPositiveLifetime() {
        assertThatThrownBy(() -> new JwtService(TestSigningKeys.VALID, 0, TEST_ISSUER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("token-ttl-seconds");
    }

    // --------------------------------------------------
    // Generation
    // --------------------------------------------------

    @Test
    void generatesSignedTokenWithOnlyNecessaryClaims() throws Exception {
        UUID userId = UUID.randomUUID();

        IssuedAccessToken issued = jwtService.generateAccessToken(userId, UserRole.CANDIDATE);

        SignedJWT parsed = SignedJWT.parse(issued.token());
        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
        JWTClaimsSet claims = parsed.getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.getIssuer()).isEqualTo(TEST_ISSUER);
        assertThat(claims.getIssueTime()).isNotNull();
        assertThat(claims.getExpirationTime()).isNotNull();
        assertThat(claims.getClaims().keySet())
                .containsExactlyInAnyOrder("sub", "iss", "iat", "exp", "role");
        assertThat(claims.getStringClaim("role")).isEqualTo("CANDIDATE");
    }

    @Test
    void tokenLifetimeMatchesConfiguration() {
        IssuedAccessToken issued = jwtService.generateAccessToken(UUID.randomUUID(), UserRole.CANDIDATE);

        assertThat(Duration.between(issued.expiresAt().minusSeconds(1), issued.expiresAt()))
                .isEqualTo(Duration.ofSeconds(1));
        assertThat(issued.expiresInSeconds()).isBetween(TEST_TTL.toSeconds() - 5, TEST_TTL.toSeconds());
        assertThat(jwtService.tokenTtl()).isEqualTo(TEST_TTL);
    }

    // --------------------------------------------------
    // Validation round trip
    // --------------------------------------------------

    @Test
    void validatesOwnTokenAndReturnsStableUserId() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId, UserRole.RECRUITER).token();

        UUID subject = jwtService.validateAccessToken(token);

        assertThat(subject).isEqualTo(userId);
    }

    @Test
    void trimsSurroundingWhitespaceFromTokenInput() {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), UserRole.CANDIDATE).token();

        assertThat(jwtService.validateAccessToken("  " + token + "  ")).isNotNull();
    }

    // --------------------------------------------------
    // Rejections
    // --------------------------------------------------

    @Test
    void rejectsExpiredToken() throws Exception {
        // Properly signed by the same key but already expired.
        Instant past = Instant.now().minusSeconds(120);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer(TEST_ISSUER)
                .issueTime(Date.from(past.minusSeconds(60)))
                .expirationTime(Date.from(past))
                .build();
        SignedJWT expired = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        expired.sign(new MACSigner(TestSigningKeys.VALID.getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> jwtService.validateAccessToken(expired.serialize()))
                .isInstanceOf(InvalidTokenException.class)
                .satisfies(ex -> assertThat(((InvalidTokenException) ex).reason())
                        .isEqualTo(InvalidTokenException.Reason.EXPIRED));
    }

    @Test
    void rejectsTokenSignedWithADifferentKey() {
        JwtService otherDeployment = new JwtService(TestSigningKeys.OTHER,
                TEST_TTL.toSeconds(), TEST_ISSUER);
        String foreignToken = otherDeployment
                .generateAccessToken(UUID.randomUUID(), UserRole.CANDIDATE).token();

        assertThatThrownBy(() -> jwtService.validateAccessToken(foreignToken))
                .isInstanceOf(InvalidTokenException.class)
                .satisfies(ex -> assertThat(((InvalidTokenException) ex).reason())
                        .isEqualTo(InvalidTokenException.Reason.INVALID_SIGNATURE));
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), UserRole.CANDIDATE).token();
        String tampered = flipSignatureCharacter(token);

        assertThatThrownBy(() -> jwtService.validateAccessToken(tampered))
                .isInstanceOf(InvalidTokenException.class)
                .satisfies(ex -> assertThat(((InvalidTokenException) ex).reason())
                        .isEqualTo(InvalidTokenException.Reason.INVALID_SIGNATURE));
    }

    @Test
    void rejectsModifiedPayload() throws Exception {
        // An attacker who swaps the claims but cannot re-sign: the original signature no
        // longer matches the modified payload, so the token is rejected outright.
        UUID originalSubject = UUID.randomUUID();
        String token = jwtService.generateAccessToken(originalSubject, UserRole.CANDIDATE).token();
        SignedJWT parsed = SignedJWT.parse(token);
        JWTClaimsSet forged = new JWTClaimsSet.Builder(parsed.getJWTClaimsSet())
                .subject(UUID.randomUUID().toString())
                .build();
        // Keep the original (now stale) signature under the forged payload.
        String[] parts = token.split("\\.");
        String forgedToken = parts[0] + "."
                + com.nimbusds.jose.util.Base64URL.encode(forged.toJSONObject().toString()) + "."
                + parts[2];

        assertThatThrownBy(() -> jwtService.validateAccessToken(forgedToken))
                .isInstanceOf(InvalidTokenException.class)
                .satisfies(ex -> assertThat(((InvalidTokenException) ex).reason())
                        .isEqualTo(InvalidTokenException.Reason.INVALID_SIGNATURE));
    }

    @Test
    void rejectsTokenWithAnotherSigningAlgorithm() throws Exception {
        // Validly signed — but with HS384 instead of the mandated HS256.
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer(TEST_ISSUER)
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plus(TEST_TTL)))
                .build();
        SignedJWT hs384 = new SignedJWT(new JWSHeader(JWSAlgorithm.HS384), claims);
        hs384.sign(new MACSigner(TestSigningKeys.VALID.getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> jwtService.validateAccessToken(hs384.serialize()))
                .isInstanceOf(InvalidTokenException.class)
                .satisfies(ex -> assertThat(((InvalidTokenException) ex).reason())
                        .isEqualTo(InvalidTokenException.Reason.UNSUPPORTED_ALGORITHM));
    }

    @Test
    void rejectsUnsignedAlgNoneToken() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer(TEST_ISSUER)
                .expirationTime(Date.from(Instant.now().plus(TEST_TTL)))
                .build();
        String algNone = new PlainJWT(claims).serialize();

        assertThatThrownBy(() -> jwtService.validateAccessToken(algNone))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsMalformedTokens() {
        assertThatThrownBy(() -> jwtService.validateAccessToken("not-a-token"))
                .isInstanceOf(InvalidTokenException.class)
                .satisfies(ex -> assertThat(((InvalidTokenException) ex).reason())
                        .isEqualTo(InvalidTokenException.Reason.MALFORMED));
        assertThatThrownBy(() -> jwtService.validateAccessToken("a.b.c"))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> jwtService.validateAccessToken(""))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> jwtService.validateAccessToken(null))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> jwtService.validateAccessToken("   "))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsTokenMissingSubjectClaim() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(TEST_ISSUER)
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plus(TEST_TTL)))
                .build();
        SignedJWT subjectless = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        subjectless.sign(new MACSigner(TestSigningKeys.VALID.getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> jwtService.validateAccessToken(subjectless.serialize()))
                .isInstanceOf(InvalidTokenException.class)
                .satisfies(ex -> assertThat(((InvalidTokenException) ex).reason())
                        .isEqualTo(InvalidTokenException.Reason.MISSING_CLAIMS));
    }

    @Test
    void rejectsTokenWithNonUuidSubject() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("not-a-uuid")
                .issuer(TEST_ISSUER)
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plus(TEST_TTL)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(TestSigningKeys.VALID.getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> jwtService.validateAccessToken(jwt.serialize()))
                .isInstanceOf(InvalidTokenException.class)
                .satisfies(ex -> assertThat(((InvalidTokenException) ex).reason())
                        .isEqualTo(InvalidTokenException.Reason.MISSING_CLAIMS));
    }

    @Test
    void rejectsTokenFromAnotherIssuer() throws Exception {
        JwtService foreignIssuer = new JwtService(TestSigningKeys.VALID, TEST_TTL.toSeconds(),
                "someone-else.example");
        String foreignToken = foreignIssuer
                .generateAccessToken(UUID.randomUUID(), UserRole.CANDIDATE).token();

        assertThatThrownBy(() -> jwtService.validateAccessToken(foreignToken))
                .isInstanceOf(InvalidTokenException.class)
                .satisfies(ex -> assertThat(((InvalidTokenException) ex).reason())
                        .isEqualTo(InvalidTokenException.Reason.INVALID_ISSUER));
    }

    @Test
    void rejectionMessagesNeverContainTheSigningKey() {
        assertThatThrownBy(() -> jwtService.validateAccessToken("not-a-token"))
                .hasMessageNotContaining(TestSigningKeys.VALID);
    }

    /**
     * Flips one base64url character in the MIDDLE of the signature.
     *
     * <p>The final character of a 32-byte HMAC's base64url encoding carries padding bits
     * after its four data bits, so flipping only that character can decode back to the
     * identical byte array and leave the signature valid. A middle character always holds
     * six real data bits, so the tamper is guaranteed to change the signature bytes.</p>
     */
    private static String flipSignatureCharacter(String token) {
        int lastDot = token.lastIndexOf('.');
        String signature = token.substring(lastDot + 1);
        int middle = signature.length() / 2;
        char original = signature.charAt(middle);
        return token.substring(0, lastDot + 1)
                + signature.substring(0, middle) + (original == 'A' ? 'B' : 'A')
                + signature.substring(middle + 1);
    }
}
