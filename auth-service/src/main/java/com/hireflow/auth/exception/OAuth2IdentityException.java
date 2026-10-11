package com.hireflow.auth.exception;

import org.springframework.http.HttpStatus;

import com.hireflow.auth.error.ErrorCode;

/**
 * Raised when a verified external identity (for example a Google account) cannot be turned
 * into an authenticated HireFlow session for a specific, safe reason.
 *
 * <p>The {@link Reason} carries the deliberate HTTP status and application error code for
 * each outcome, so the same failure renders identically whether it surfaces through the
 * OAuth2 callback (handled by the OAuth2 failure handler) or through the authenticated
 * account-linking endpoint (handled by {@link GlobalExceptionHandler}). Only safe, fixed
 * messages are ever produced: nothing here echoes provider tokens, authorization codes,
 * claim values or stack traces to the caller.</p>
 *
 * <p>Each reason represents a deliberate security decision, not an accident:</p>
 *
 * <ul>
 *   <li>{@link Reason#MISSING_SUBJECT} / {@link Reason#MISSING_EMAIL} — the provider did not
 *       supply the stable claims the account mapping depends on, so sign-in is refused rather
 *       than guessing an identity;</li>
 *   <li>{@link Reason#EMAIL_NOT_VERIFIED} — the provider did not confirm the email is
 *       verified, and an unverified email is never trusted to identify a person;</li>
 *   <li>{@link Reason#ACCOUNT_DISABLED} — the HireFlow account is disabled;</li>
 *   <li>{@link Reason#EMAIL_ALREADY_REGISTERED} — the Google identity is not linked yet but
 *       its email matches an existing account; automatic linking would be an account-takeover
 *       vector, so it is refused and the user is told to link from an authenticated session;</li>
 *   <li>{@link Reason#IDENTITY_ALREADY_LINKED} — the Google identity is already linked to a
 *       different HireFlow user.</li>
 * </ul>
 */
public class OAuth2IdentityException extends RuntimeException {

    /** A deliberate security decision, each with its own status, code and safe message. */
    public enum Reason {

        /** The provider supplied no stable subject identifier; identity cannot be mapped. */
        MISSING_SUBJECT(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST,
                "The identity provider did not supply a subject identifier"),

        /** The provider supplied no email address. */
        MISSING_EMAIL(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST,
                "The identity provider did not supply an email address"),

        /** The provider did not mark the email as verified; it is never trusted unverified. */
        EMAIL_NOT_VERIFIED(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED,
                "The email address for this account is not verified"),

        /** The matched HireFlow account is disabled. */
        ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                "This HireFlow account is disabled"),

        /**
         * The identity is not linked yet but its email matches an existing account. The
         * accounts are deliberately not merged: linking must happen from an authenticated
         * session so a Google login can never take over an existing account.
         */
        EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                "An account with this email already exists. Sign in with your existing "
                        + "credentials, then link Google from your account settings."),

        /** The identity is already linked to a different HireFlow user. */
        IDENTITY_ALREADY_LINKED(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                "This external account is already linked to a different HireFlow user");

        private final HttpStatus status;
        private final ErrorCode errorCode;
        private final String defaultMessage;

        Reason(HttpStatus status, ErrorCode errorCode, String defaultMessage) {
            this.status = status;
            this.errorCode = errorCode;
            this.defaultMessage = defaultMessage;
        }

        public HttpStatus status() {
            return status;
        }

        public ErrorCode errorCode() {
            return errorCode;
        }

        public String defaultMessage() {
            return defaultMessage;
        }
    }

    private final Reason reason;

    public OAuth2IdentityException(Reason reason) {
        super(reason.defaultMessage());
        this.reason = reason;
    }

    public OAuth2IdentityException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public OAuth2IdentityException(Reason reason, Throwable cause) {
        super(reason.defaultMessage(), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
