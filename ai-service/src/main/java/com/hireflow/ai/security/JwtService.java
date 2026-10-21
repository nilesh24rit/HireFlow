package com.hireflow.ai.security;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

@Component
public class JwtService {

    private final byte[] signingKey;
    private final String issuer;

    public JwtService(@Value("${hireflow.jwt.signing-key:}") String signingKey,
            @Value("${hireflow.jwt.issuer:hireflow-auth}") String issuer) {
        if (signingKey == null || signingKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT signing key is not configured or is too short");
        }
        this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8);
        this.issuer = issuer;
    }

    public AuthenticatedUser authenticate(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.verify(new MACVerifier(signingKey))) {
                throw new IllegalArgumentException("invalid signature");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null || !expiration.toInstant().isAfter(Instant.now())
                    || !issuer.equals(claims.getIssuer())) {
                throw new IllegalArgumentException("invalid claims");
            }
            String role = claims.getStringClaim("role");
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("missing role");
            }
            return new AuthenticatedUser(UUID.fromString(claims.getSubject()), role);
        } catch (ParseException | JOSEException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid token", ex);
        }
    }
}
