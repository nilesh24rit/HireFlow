package com.hireflow.auth.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.hireflow.auth.error.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Renders a failed Google sign-in in the common HireFlow error contract.
 *
 * <p>Every failure the redirect flow can produce reaches this handler — a rejection at
 * Google, a callback that cannot be tied back to a pending sign-in, a broken exchange with
 * Google, or any unexpected authentication error. The deliberate rule is that the response
 * always says something useful and never says anything true about our internals: it maps
 * the machine-readable OAuth2 error code onto a status and one of a handful of fixed
 * messages, and never echoes the provider's error description, the {@code state} value, a
 * token, or a stack trace.</p>
 *
 * <p>The statuses are chosen, not defaulted:</p>
 *
 * <ul>
 *   <li><b>401 {@code UNAUTHENTICATED}</b> — the user cancelled or declined at Google
 *       ({@code access_denied}). Nothing is wrong; they simply did not finish.</li>
 *   <li><b>400 {@code INVALID_REQUEST}</b> — the callback itself cannot be trusted: the
 *       {@code state} does not match, no pending sign-in exists for this browser (an
 *       expired or lost session), or the request is malformed. The right answer is to start
 *       again, which the message says.</li>
 *   <li><b>500 {@code INTERNAL_ERROR}</b> — Google reported a server-side fault, or
 *       authentication failed for a reason that is genuinely ours. Blaming the user would
 *       be misleading, so the response stays generic and reveals nothing.</li>
 * </ul>
 *
 * <p>Identity refusals that happen <i>after</i> Google has already authenticated the user
 * — a disabled account, an unverified email, an email that already belongs to another
 * account — are decided by {@link GoogleIdentityService} and therefore rendered by
 * {@link GoogleOAuth2SuccessHandler}, which owns that stage. Between the two handlers every
 * failure mode of the flow is covered.</p>
 */
@Component
public class GoogleOAuth2FailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuth2FailureHandler.class);

    private static final String CANCELLED_MESSAGE =
            "Google sign-in was cancelled or declined";
    private static final String UNVERIFIABLE_MESSAGE =
            "The Google sign-in request could not be verified. Please start again.";
    private static final String GENERIC_FAILURE_MESSAGE = "Google sign-in failed";
    private static final String UNEXPECTED_MESSAGE = "An unexpected error occurred";

    /**
     * The OAuth2 error code Spring Security emits when the {@code state} of the callback
     * does not match the pending authorization request it was compared against.
     */
    private static final String INVALID_STATE_PARAMETER = "invalid_state_parameter";

    /**
     * The code emitted when no pending authorization request exists for the callback at
     * all — an expired, lost or foreign session, which is exactly the "invalid state"
     * outcome a user reaches by starting a second sign-in or returning to a stale tab.
     */
    private static final String AUTHORIZATION_REQUEST_NOT_FOUND = "authorization_request_not_found";

    private final OAuth2ResponseWriter responseWriter;

    public GoogleOAuth2FailureHandler(OAuth2ResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            // Only the machine-readable code is logged; the provider's description may
            // carry response detail and is deliberately not read or echoed.
            String errorCode = oauth2Exception.getError().getErrorCode();
            log.debug("Google OAuth2 login failed: {}", errorCode);
            respondFor(request, response, errorCode);
            return;
        }

        if (exception instanceof InternalAuthenticationServiceException) {
            // A server-side failure while authenticating — a database outage, a broken
            // registration lookup. The problem is ours, so the response says so generically.
            log.error("Google OAuth2 login failed unexpectedly", exception);
            responseWriter.writeError(request, response, HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.INTERNAL_ERROR, UNEXPECTED_MESSAGE);
            return;
        }

        log.debug("Google OAuth2 login failed: {}", exception.getClass().getSimpleName());
        responseWriter.writeError(request, response, HttpStatus.UNAUTHORIZED,
                ErrorCode.UNAUTHENTICATED, GENERIC_FAILURE_MESSAGE);
    }

    private void respondFor(HttpServletRequest request, HttpServletResponse response,
            String errorCode) throws IOException {
        switch (errorCode) {
            case OAuth2ErrorCodes.ACCESS_DENIED -> responseWriter.writeError(request, response,
                    HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, CANCELLED_MESSAGE);
            case INVALID_STATE_PARAMETER, AUTHORIZATION_REQUEST_NOT_FOUND,
                    OAuth2ErrorCodes.INVALID_REQUEST -> responseWriter.writeError(request,
                    response, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST,
                    UNVERIFIABLE_MESSAGE);
            // Google reported a fault on its side, or one we caused during the exchange.
            case OAuth2ErrorCodes.SERVER_ERROR, OAuth2ErrorCodes.TEMPORARILY_UNAVAILABLE ->
                responseWriter.writeError(request, response, HttpStatus.INTERNAL_SERVER_ERROR,
                        ErrorCode.INTERNAL_ERROR, UNEXPECTED_MESSAGE);
            // Anything else — a broken token exchange, an unknown error — is reported
            // generically rather than guessed at or echoed back.
            default -> responseWriter.writeError(request, response, HttpStatus.UNAUTHORIZED,
                    ErrorCode.UNAUTHENTICATED, GENERIC_FAILURE_MESSAGE);
        }
    }
}
