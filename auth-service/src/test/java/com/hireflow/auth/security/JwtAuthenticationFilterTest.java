package com.hireflow.auth.security;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.hireflow.auth.entity.UserRole;

import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests of the bearer-token filter: the security context is populated only after
 * successful validation, failures leave the request unauthenticated but marked, and no
 * path trusts anything but a verified token.
 *
 * <p>The context is observed <i>during</i> filter-chain execution (via a capturing chain),
 * because the filter deliberately clears the thread-bound context once the request
 * completes.</p>
 */
class JwtAuthenticationFilterTest {

    private static final Duration TTL = Duration.ofHours(1);
    private static final String ISSUER = "hireflow-auth";

    private final JwtService jwtService = new JwtService(TestSigningKeys.VALID, TTL.toSeconds(), ISSUER);

    private final AtomicReference<Authentication> authenticationDuringChain = new AtomicReference<>();

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService);
        SecurityContextHolder.clearContext();
        authenticationDuringChain.set(null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenPopulatesSecurityContextWithStableSubject() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId, UserRole.CANDIDATE).token();

        MockHttpServletRequest request = requestWithAuthorization("Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), capturingChain());

        Authentication authentication = authenticationDuringChain.get();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(userId.toString());
        assertThat(authentication.getAuthorities()).isEmpty();
        assertThat(request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE)).isNull();
    }

    @Test
    void acceptsBearerSchemeCaseInsensitively() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId, UserRole.RECRUITER).token();

        filter.doFilter(requestWithAuthorization("bearer " + token),
                new MockHttpServletResponse(), capturingChain());

        assertThat(authenticationDuringChain.get()).isNotNull();
    }

    @Test
    void missingAuthorizationHeaderLeavesRequestUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/x");

        filter.doFilter(request, new MockHttpServletResponse(), capturingChain());

        assertThat(authenticationDuringChain.get()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE)).isNull();
    }

    @Test
    void nonBearerSchemeIsIgnoredNotRejected() throws Exception {
        MockHttpServletRequest request = requestWithAuthorization("Basic dXNlcjpwYXNz");

        filter.doFilter(request, new MockHttpServletResponse(), capturingChain());

        assertThat(authenticationDuringChain.get()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE)).isNull();
    }

    @Test
    void emptyBearerTokenIsMarkedInvalid() throws Exception {
        MockHttpServletRequest request = requestWithAuthorization("Bearer ");

        filter.doFilter(request, new MockHttpServletResponse(), capturingChain());

        assertThat(authenticationDuringChain.get()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE)).isNotNull();
    }

    @Test
    void malformedTokenIsMarkedInvalidAndUnauthenticated() throws Exception {
        MockHttpServletRequest request = requestWithAuthorization("Bearer not-a-token");

        filter.doFilter(request, new MockHttpServletResponse(), capturingChain());

        assertThat(authenticationDuringChain.get()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE)).isNotNull();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        JwtService foreignDeployment = new JwtService(TestSigningKeys.OTHER, TTL.toSeconds(), ISSUER);
        String foreignToken = foreignDeployment.generateAccessToken(UUID.randomUUID(), UserRole.CANDIDATE).token();

        MockHttpServletRequest request = requestWithAuthorization("Bearer " + foreignToken);
        filter.doFilter(request, new MockHttpServletResponse(), capturingChain());

        assertThat(authenticationDuringChain.get()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE)).isNotNull();
    }

    @Test
    void contextNeverBleedsFromOneRequestToTheNext() throws Exception {
        // The token authenticates exactly one request: the thread-bound context is cleared
        // after the chain, so a later request reusing the thread starts unauthenticated.
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId, UserRole.CANDIDATE).token();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(requestWithAuthorization("Bearer " + token), new MockHttpServletResponse(), chain);

        // The authenticated context was in place while the chain ran (the request was
        // forwarded) but is gone the moment the request completes.
        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/users/x"),
                new MockHttpServletResponse(), capturingChain());

        assertThat(authenticationDuringChain.get()).isNull();
    }

    @Test
    void filterNeverWritesAResponseItself() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWithAuthorization("Bearer invalid"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
    }

    /** A chain that records the security context as the controller would observe it. */
    private FilterChain capturingChain() {
        return (request, response) ->
                authenticationDuringChain.set(SecurityContextHolder.getContext().getAuthentication());
    }

    private MockHttpServletRequest requestWithAuthorization(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/x");
        request.addHeader("Authorization", authorization);
        return request;
    }
}
