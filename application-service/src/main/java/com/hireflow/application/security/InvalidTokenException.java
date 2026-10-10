package com.hireflow.application.security;

/**
 * Raised when a bearer token cannot be trusted. The reason is classified so tests and the
 * security filter can react deliberately, while clients only ever see the generic 401
 * contract — never claims, signatures or any detail from the token itself.
 */
public class InvalidTokenException extends RuntimeException {

    /** Why a token was rejected. Never exposed to clients. */
    public enum Reason {
        MALFORMED,
        UNSUPPORTED_ALGORITHM,
        INVALID_SIGNATURE,
        EXPIRED,
        INVALID_ISSUER,
        MISSING_CLAIMS
    }

    private final Reason reason;

    public InvalidTokenException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
