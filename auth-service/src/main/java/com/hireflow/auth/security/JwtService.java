package com.hireflow.auth.security;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.hireflow.auth.entity.UserRole;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Issues and validates HireFlow access tokens.
 *
 * <p><b>Token design.</b> A compact, signed JWT (HS256) carrying only what services need:
 * {@code sub} — the stable user id (UUID), {@code iat}, {@code exp}, {@code iss}, and a
 * {@code role} claim derived server-side from the persisted user record at issuance time.
 * No names, emails, permissions or other profile data are embedded.</p>
 *
 * <p><b>Signing configuration.</b> The HMAC signing key comes exclusively from the
 * {@code hireflow.jwt.signing-key} property (typically supplied through the
 * {@code HIREFLOW_JWT_SIGNINGKEY} environment variable). There is no default, no
 * fallback and no hardcoded key anywhere: the service fails safely at startup when the
 * key is missing or too short, rather than silently signing with a known secret. Keys
 * must have at least 32 bytes of entropy (the HS256 requirement) and should be produced
 * by a cryptographically secure source. Token lifetime ({@code hireflow.jwt.token-ttl-seconds},
 * default 1 hour) and issuer ({@code hireflow.jwt.issuer}, default {@code hireflow-auth})
 * are configurable; the lifetime default only shortens exposure and does not weaken
 * signing.</p>
 *
 * <p><b>Validation.</b> {@link #validateAccessToken(String)} checks, in order: the token
 * parses as a signed JWT, the algorithm is exactly HS256 (so {@code alg=none} or a
 * substituted asymmetric algorithm is rejected), the HMAC signature verifies against the
 * configured key, the token is not expired, the issuer matches, and the required subject
 * claim is a valid user id. Malformed, expired, tampered or otherwise untrustworthy
 * tokens are rejected with {@link InvalidTokenException}; the exception reason and message
 * stay server-side and are never echoed to clients. Tokens are never logged.</p>
 */
@Component
public class JwtService {

    /** The only accepted signing algorithm; HS256 is matched exactly on validation. */
    static final JWSAlgorithm ALGORITHM = JWSAlgorithm.HS256;

    /** HS256 keys shorter than 256 bits are rejected by the JOSE layer and by policy. */
    static final int MINIMUM_KEY_BYTES = 32;

    private final byte[] signingKey;
    private final Duration tokenTtl;
    private final String issuer;

    public JwtService(
            @Value("${hireflow.jwt.signing-key:}") String signingKey,
            @Value("${hireflow.jwt.token-ttl-seconds:3600}") long tokenTtlSeconds,
            @Value("${hireflow.jwt.issuer:hireflow-auth}") String issuer) {
        this.signingKey = requireSigningKey(signingKey);
        if (tokenTtlSeconds <= 0) {
            throw new IllegalStateException(
                    "hireflow.jwt.token-ttl-seconds must be a positive number of seconds but was "
                            + tokenTtlSeconds);
        }
        this.tokenTtl = Duration.ofSeconds(tokenTtlSeconds);
        this.issuer = issuer;
    }

    /**
     * Signs a fresh access token for the given user identity.
     *
     * @param userId the stable user id embedded as the subject claim
     * @param role   the user's persisted role, embedded as a derived claim
     * @return the serialized token and its expiry instant
     */
    public IssuedAccessToken generateAccessToken(UUID userId, UserRole role) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(tokenTtl);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issuer(issuer)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .claim("role", role.name())
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(ALGORITHM), claims);
        try {
            jwt.sign(new MACSigner(signingKey));
        } catch (JOSEException ex) {
            throw new IllegalStateException("Failed to sign access token", ex);
        }
        return new IssuedAccessToken(jwt.serialize(), expiresAt);
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
            // Rejects alg=none and any substituted algorithm before any crypto runs.
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
                    "token was not issued by this service");
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

    /** Configured lifetime of issued tokens, surfaced as {@code expiresIn} on login. */
    public Duration tokenTtl() {
        return tokenTtl;
    }

    private static byte[] requireSigningKey(String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new IllegalStateException(
                    "JWT signing key is not configured. Set the hireflow.jwt.signing-key property"
                            + " (for example through the HIREFLOW_JWT_SIGNINGKEY environment variable)"
                            + " to a random value of at least " + MINIMUM_KEY_BYTES
                            + " characters. The service refuses to start without it — no default"
                            + " signing key exists.");
        }
        byte[] keyBytes = configuredKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT signing key is too short: at least " + MINIMUM_KEY_BYTES
                            + " bytes (256 bits) are required for HS256.");
        }
        // Defensive copy so the key cannot be mutated through the original reference.
        return keyBytes.clone();
    }
}
