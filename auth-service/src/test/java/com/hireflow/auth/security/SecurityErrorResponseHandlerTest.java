package com.hireflow.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import tools.jackson.databind.json.JsonMapper;

/**
 * Unit tests of the deliberate security responses of Step 12.
 *
 * <p>The 403 access-denied branch has no live trigger yet (role rules arrive with RBAC in
 * Step 15), so its exact shape — and the parts of the 401 branch that are hard to observe
 * from outside, such as correlation-id handling — are pinned here directly against the
 * servlet mocks. The live 401 behaviour over real HTTP is covered by
 * {@code AuthSecurityBaselineTest}, and the filter-chain behaviour by
 * {@code SecurityConfigurationTest}.
 */
class SecurityErrorResponseHandlerTest {

    private static final String REQUEST_PATH = "/api/users/42";

    private SecurityErrorResponseHandler handler;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        handler = new SecurityErrorResponseHandler(JsonMapper.builder().build());
        request = new MockHttpServletRequest("GET", REQUEST_PATH);
        response = new MockHttpServletResponse();
    }

    @Test
    void authenticationRejectionWrites401WithBearerChallengeAndContract() throws Exception {
        handler.commence(request, response, new BadCredentialsException("super-secret-detail"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).startsWith("Bearer");
        assertThat(response.getContentType()).startsWith("application/json");

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("\"status\":401")
                .contains("\"error\":\"Unauthorized\"")
                .contains("\"code\":\"UNAUTHENTICATED\"")
                .contains("\"message\":\"Authentication required\"")
                .contains("\"path\":\"" + REQUEST_PATH + "\"")
                // the failure itself must never be echoed to the caller
                .doesNotContain("super-secret-detail")
                .doesNotContain("BadCredentials")
                .doesNotContain("Exception")
                .doesNotContain("stackTrace")
                .doesNotContain("trace");
    }

    @Test
    void rejectedBearerTokenProducesInvalidTokenChallengeWithGenericBody() throws Exception {
        // The filter marks failed validations; the entry point then advertises
        // error="invalid_token" while the body stays the same generic contract —
        // no claims, no crypto detail, no token material.
        request.setAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE, Boolean.TRUE);

        handler.commence(request, response, new BadCredentialsException("expired JWT!"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate"))
                .isEqualTo("Bearer realm=\"HireFlow auth-service\", error=\"invalid_token\"");

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("\"message\":\"Invalid or expired authentication token\"")
                .doesNotContain("expired JWT!")
                .doesNotContain("claims")
                .doesNotContain("signature");
    }

    @Test
    void accessDenialWrites403ContractWithoutChallenge() throws Exception {
        handler.handle(request, response, new AccessDeniedException("internal-denial-detail"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("WWW-Authenticate")).isNull();
        assertThat(response.getContentType()).startsWith("application/json");

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("\"status\":403")
                .contains("\"error\":\"Forbidden\"")
                .contains("\"code\":\"FORBIDDEN\"")
                .contains("\"message\":\"Access denied\"")
                .contains("\"path\":\"" + REQUEST_PATH + "\"")
                .doesNotContain("internal-denial-detail")
                .doesNotContain("Exception");
    }

    @Test
    void echoesCallerSuppliedCorrelationId() throws Exception {
        request.addHeader("X-Request-Id", "corr-123");

        handler.commence(request, response, new BadCredentialsException("detail"));

        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"requestId\":\"corr-123\"");
    }

    @Test
    void truncatesOverlongCorrelationId() throws Exception {
        request.addHeader("X-Request-Id", "x".repeat(100));

        handler.commence(request, response, new BadCredentialsException("detail"));

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"requestId\":\"" + "x".repeat(64) + "\"");
        assertThat(body).doesNotContain("x".repeat(65));
    }

    @Test
    void omitsCorrelationIdWhenCallerSuppliedNone() throws Exception {
        request.addHeader("X-Request-Id", "   ");

        handler.commence(request, response, new BadCredentialsException("detail"));

        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("requestId");
    }

    @Test
    void echoesRandomPathWithoutAlteringIt() throws Exception {
        String path = "/api/jobs/" + UUID.randomUUID();
        MockHttpServletRequest otherRequest = new MockHttpServletRequest("DELETE", path);

        handler.handle(otherRequest, response, new AccessDeniedException("detail"));

        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"path\":\"" + path + "\"");
    }
}
