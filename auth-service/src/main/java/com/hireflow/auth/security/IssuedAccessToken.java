package com.hireflow.auth.security;

import java.time.Duration;
import java.time.Instant;

/**
 * A freshly minted access token together with the instant it expires.
 *
 * @param token     the serialized signed JWT
 * @param expiresAt the expiry instant carried inside the token
 */
public record IssuedAccessToken(String token, Instant expiresAt) {

    /** Seconds left until expiry, floored at zero. */
    public long expiresInSeconds() {
        return Math.max(0, Duration.between(Instant.now(), expiresAt).getSeconds());
    }
}
