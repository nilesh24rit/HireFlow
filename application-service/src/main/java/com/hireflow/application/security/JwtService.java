package com.hireflow.application.security;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Validation of HireFlow access tokens.
 *
 * <p>Candidate-service does not issue tokens — auth-service does — but it verifies them
 * itself, so identity is never taken on trust from a header or from the gateway. This is
 * the same validation as auth-service's {@code JwtService}, validate-only: the algorithm
 * must be exactly HS256 (so {@code alg=none} or a substituted algorithm is rejected), the
 * HMAC signature must verify against the shared signing key, the token must not be
 * expired, the issuer must match, and the subject must be a valid user id. Malformed,
 * expired, tampered or wrongly issued tokens are rejected with
 * {@link InvalidTokenException}; reasons stay server-side.</p>
 *
 * <p>The signing key comes exclusively from the {@code hireflow.jwt.signing-key} property
 * (for example the {@code HIREFLOW_JWT_SIGNINGKEY} environment variable) shared with
 * auth-service. There is no default and no fallback: the service fails safely at startup
 * when the key is missing or too short. Tokens are never logged.</p>
 */
@Component
public class JwtService {

    /** The only accepted signing algorithm; HS256 is matched exactly on validation. */
    static final JWSAlgorithm ALGORITHM = JWSAlgorithm.HS256;

    /** HS256 keys shorter than 256 bits are rejected by the JOSE layer and by policy. */
    static final int MINIMUM_KEY_BYTES = 32;

    private final byte[] signingKey;
    private final String issuer;

    public JwtService(
            @Value("${hireflow.jwt.signing-key:}") String signingKey,
            @Value("${hireflow.jwt.issuer:hireflow-auth}") String issuer) {
        this.signingKey = requireSigningKey(signingKey);
        this.issuer = issuer;
    }

    /**
     * Validates a bearer token and returns the authenticated user's id.
     *
     * @param token the raw token value taken from the {@code Authorization} header
     * @return the stable user id carried in the subject claim
     * @throws InvalidTokenException when the token is malformed, tampered with, expired,
     *                               wrongly issued or missing required claims
     */
    public UUID validateAccessToken(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException(InvalidTokenException.Reason.MALFORMED,
                    "token is empty");
        }

        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(token.trim());
        } catch (ParseException ex) {
            throw new InvalidTokenException(InvalidTokenException.Reason.MALFORMED,
                    "token is not a parsable signed JWT");
        }

        if (!ALGORITHM.equals(jwt.getHeader().getAlgorithm())) {
            throw new InvalidTokenException(InvalidTokenException.Reason.UNSUPPORTED_ALGORITHM,
                    "token does not use the required signing algorithm");
        }

        boolean signatureValid;
        try {
            signatureValid = jwt.verify(new MACVerifier(signingKey));
        } catch (JOSEException ex) {
            throw new InvalidTokenException(InvalidTokenException.Reason.INVALID_SIGNATURE,
                    "token signature could not be verified");
        }
        if (!signatureValid) {
            throw new InvalidTokenException(InvalidTokenException.Reason.INVALID_SIGNATURE,
                    "token signature is invalid");
        }

        JWTClaimsSet claims;
        try {
            claims = jwt.getJWTClaimsSet();
        } catch (ParseException ex) {
            throw new InvalidTokenException(InvalidTokenException.Reason.MALFORMED,
                    "token claims are not parsable");
        }

        Date expiresAt = claims.getExpirationTime();
        if (expiresAt == null || !expiresAt.toInstant().isAfter(Instant.now())) {
            throw new InvalidTokenException(InvalidTokenException.Reason.EXPIRED,
                    "token has expired");
        }

        if (issuer != null && !issuer.equals(claims.getIssuer())) {
            throw new InvalidTokenException(InvalidTokenException.Reason.INVALID_ISSUER,
                    "token was not issued by the HireFlow auth service");
        }

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new InvalidTokenException(InvalidTokenException.Reason.MISSING_CLAIMS,
                    "token is missing the subject claim");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw new InvalidTokenException(InvalidTokenException.Reason.MISSING_CLAIMS,
                    "token subject is not a valid user id");
        }
    }

    private static byte[] requireSigningKey(String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new IllegalStateException(
                    "JWT signing key is not configured. Set the hireflow.jwt.signing-key property"
                            + " (for example through the HIREFLOW_JWT_SIGNINGKEY environment variable)"
                            + " to the value shared by HireFlow services. This service refuses to"
                            + " start without it — no default signing key exists.");
        }
        byte[] keyBytes = configuredKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT signing key is too short: at least " + MINIMUM_KEY_BYTES
                            + " bytes (256 bits) are required for HS256.");
        }
        return keyBytes.clone();
    }
}
