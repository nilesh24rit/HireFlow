package com.hireflow.job.security;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Stateless bearer-token authentication filter (Step 13).
 *
 * <p>For every request:</p>
 * <ol>
 *   <li>the {@code Authorization} header is read; anything that is not {@code Bearer
 *       <token>} passes through unauthenticated (the authorization rules then decide
 *       whether anonymous access is acceptable);</li>
 *   <li>the token is validated by {@link JwtService} — signature, algorithm, expiry,
 *       issuer and required claims;</li>
 *   <li>only after successful validation is the Spring Security context populated, with
 *       the token's subject (the stable user id) as the principal. No role or identity is
 *       ever taken from unverified input: the validated token is the sole source;</li>
 *   <li>on any validation failure the request continues <i>without</i> authentication and
 *       a request attribute marks the bad token, so the entry point can answer 401 with a
 *       precise {@code invalid_token} challenge while the client-visible body stays
 *       generic. An invalid token on a protected route can never degrade into anonymous
 *       success because those routes require authentication.</li>
 * </ol>
 *
 * <p>The filter is deliberately thin: no repository lookups, no business logic, no
 * response writing. It runs once per request and never logs the token itself.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Request attribute marking that a supplied bearer token failed validation. */
    public static final String INVALID_TOKEN_ATTRIBUTE =
            JwtAuthenticationFilter.class.getName() + ".invalidToken";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_SCHEME = "Bearer";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && isBearerScheme(header)) {
                applyBearerAuthentication(request, header);
            }
            // When no (or a foreign-scheme) credential is supplied, the request continues
            // unauthenticated and the authorization rules decide — the context is whatever
            // this request itself established (for example a test post-processor).
            filterChain.doFilter(request, response);
        } finally {
            // The token authenticates exactly one request. Clearing the thread-bound
            // context after the chain guarantees nothing bleeds onto a later request
            // reusing this container thread.
            SecurityContextHolder.clearContext();
        }
    }

    private void applyBearerAuthentication(HttpServletRequest request, String header) {
        String token = bearerToken(header);
        if (token.isEmpty()) {
            markInvalidToken(request);
            return;
        }
        try {
            UUID userId = jwtService.validateAccessToken(token);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (InvalidTokenException ex) {
            // Reason and message stay server-side; clients only see the generic 401.
            log.debug("Rejected bearer token: {}", ex.reason());
            markInvalidToken(request);
        }
    }

    private boolean isBearerScheme(String header) {
        int space = header.indexOf(' ');
        String scheme = space < 0 ? header : header.substring(0, space);
        return BEARER_SCHEME.equalsIgnoreCase(scheme);
    }

    private String bearerToken(String header) {
        int space = header.indexOf(' ');
        return space < 0 ? "" : header.substring(space + 1).trim();
    }

    private void markInvalidToken(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        request.setAttribute(INVALID_TOKEN_ATTRIBUTE, Boolean.TRUE);
    }
}
