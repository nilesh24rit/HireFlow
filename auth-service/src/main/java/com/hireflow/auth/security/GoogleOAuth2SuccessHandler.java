package com.hireflow.auth.security;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.hireflow.auth.dto.GoogleIdentity;
import com.hireflow.auth.dto.OAuthHandoffResponse;
import com.hireflow.auth.entity.User;
import com.hireflow.auth.error.ErrorCode;
import com.hireflow.auth.exception.OAuth2IdentityException;
import com.hireflow.auth.service.GoogleIdentityService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Completes a successful Google sign-in and hands the client a way to collect its token.
 *
 * <p>This runs only after Spring Security has already finished the parts this service must
 * not re-implement: the authorization-code exchange with Google, the {@code state} check
 * that defeats login CSRF, and the signature verification of Google's ID token. By the time
 * it is called, the principal is a verified {@link OidcUser} whose claims Google itself
 * asserted.</p>
 *
 * <p><b>What it does.</b> It turns those claims into a {@link GoogleIdentity}, asks
 * {@link GoogleIdentityService} which HireFlow user that identity belongs to (registering
 * one for a first-time Google user), then signs a token with the <i>same</i>
 * {@link JwtService} credential login uses — one token format, one protected surface,
 * whichever way the user arrived.</p>
 *
 * <p><b>The token never touches a URL.</b> Redirecting a browser with the JWT in a query
 * parameter or fragment would leak it into history, logs and {@code Referer}. So the token
 * is handed to {@link OAuthHandoffService} and the response carries only a single-use,
 * short-lived code; the client redeems it at {@code POST /api/auth/oauth2/token} for the
 * ordinary {@code LoginResponse}. That is also why login CSRF stays defeated: the code
 * travels in a response body an attacker cannot read cross-origin, and the redirect that
 * got here was already validated against its {@code state}.</p>
 *
 * <p><b>Failures are deliberate too.</b> A refusal that this service decided — disabled
 * account, unverified email, an email already belonging to someone else, a missing subject
 * — surfaces as its own status through {@link OAuth2IdentityException}'s reason; anything
 * else becomes a generic 500. Neither echoes provider tokens, claim values or stack traces.
 * Tokens and handoff codes are never logged: only the event and the user id.</p>
 */
@Component
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuth2SuccessHandler.class);

    private static final String GENERIC_FAILURE_MESSAGE = "An unexpected error occurred";

    private final GoogleIdentityService googleIdentityService;
    private final JwtService jwtService;
    private final OAuthHandoffService handoffService;
    private final OAuth2ResponseWriter responseWriter;

    public GoogleOAuth2SuccessHandler(GoogleIdentityService googleIdentityService,
            JwtService jwtService, OAuthHandoffService handoffService,
            OAuth2ResponseWriter responseWriter) {
        this.googleIdentityService = googleIdentityService;
        this.jwtService = jwtService;
        this.handoffService = handoffService;
        this.responseWriter = responseWriter;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        try {
            User user = googleIdentityService.authenticateWithGoogle(
                    GoogleIdentity.fromClaims(oidcClaims(authentication)));

            // The same JwtService, claims and role handling as password login: Google is a
            // second way to obtain the one HireFlow token, never a second token format.
            IssuedAccessToken accessToken = jwtService.generateAccessToken(user.getId(), user.getRole());
            String handoffCode = handoffService.issue(user.getId(), accessToken);

            // Logged as an event only — never the code or the token it guards.
            log.debug("Google sign-in completed for user {}", user.getId());
            responseWriter.writeJson(response, HttpStatus.OK,
                    new OAuthHandoffResponse(handoffCode, handoffService.ttl().toSeconds()));
        } catch (OAuth2IdentityException ex) {
            // A refusal this service decided: its reason fixes the status, code and message.
            log.debug("Google sign-in refused: {}", ex.reason());
            responseWriter.writeError(request, response, ex.reason().status(),
                    ex.reason().errorCode(), ex.getMessage());
        } catch (RuntimeException ex) {
            // Unexpected — a database outage or a signing failure. Logged here, described
            // only as "unexpected" to the client.
            log.error("Google sign-in could not be completed", ex);
            responseWriter.writeError(request, response, HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.INTERNAL_ERROR, GENERIC_FAILURE_MESSAGE);
        } finally {
            // The transient OAuth2 principal exists for this request only. Clearing it keeps
            // the promise that no authenticated principal outlives the request that proved
            // it — the bearer token below is the sole credential from here on.
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Reads the verified claim set Google asserted.
     *
     * @param authentication the authenticated OAuth2 principal Spring Security produced
     * @return the claims to validate into a {@link GoogleIdentity}
     * @throws OAuth2IdentityException when the principal is not an OpenID Connect user,
     *                                 which would mean the flow produced no identity
     */
    private Map<String, Object> oidcClaims(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken token
                && token.getPrincipal() instanceof OidcUser oidcUser) {
            return oidcUser.getClaims();
        }
        throw new OAuth2IdentityException(OAuth2IdentityException.Reason.MISSING_SUBJECT,
                "Google sign-in did not produce an OpenID Connect identity");
    }
}
