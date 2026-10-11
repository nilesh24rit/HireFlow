package com.hireflow.auth.security;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the mapping from an OAuth2 failure to the common HireFlow error contract.
 *
 * <p>Each assertion pins a decision rather than a default: a user who cancelled at Google
 * is not an error, a callback that cannot be tied back to a pending sign-in is a bad
 * request rather than a credential problem, and a fault on Google's side is ours to report
 * generically. Across all of them the provider's own error description — which can carry
 * response detail — is never echoed.</p>
 */
class GoogleOAuth2FailureHandlerTest {

    private static final String CALLBACK_PATH = "/login/oauth2/code/google";

    private GoogleOAuth2FailureHandler handler;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        handler = new GoogleOAuth2FailureHandler(
                new OAuth2ResponseWriter(JsonMapper.builder().build()));
        request = new MockHttpServletRequest("GET", CALLBACK_PATH);
        response = new MockHttpServletResponse();
    }

    @Test
    void aCancelledSignInIsReportedAsA401NotAnError() throws Exception {
        handler.onAuthenticationFailure(request, response, oauth2Failure(OAuth2ErrorCodes.ACCESS_DENIED));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body())
                .contains("\"status\":401")
                .contains("\"code\":\"UNAUTHENTICATED\"")
                .contains("cancelled or declined")
                .contains("\"path\":\"" + CALLBACK_PATH + "\"");
    }

    @Test
    void aMismatchedStateIsReportedAsA400() throws Exception {
        handler.onAuthenticationFailure(request, response, oauth2Failure("invalid_state_parameter"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(body()).contains("\"code\":\"INVALID_REQUEST\"")
                .contains("could not be verified. Please start again");
    }

    @Test
    void aCallbackWithNoPendingSignInIsReportedAsA400() throws Exception {
        handler.onAuthenticationFailure(request, response,
                oauth2Failure("authorization_request_not_found"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(body()).contains("\"code\":\"INVALID_REQUEST\"");
    }

    @Test
    void aMalformedCallbackIsReportedAsA400() throws Exception {
        handler.onAuthenticationFailure(request, response,
                oauth2Failure(OAuth2ErrorCodes.INVALID_REQUEST));

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void aProviderFaultIsReportedAsAGeneric500() throws Exception {
        handler.onAuthenticationFailure(request, response,
                oauth2Failure(OAuth2ErrorCodes.SERVER_ERROR));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(body()).contains("\"code\":\"INTERNAL_ERROR\"")
                .contains("An unexpected error occurred");
    }

    @Test
    void anUnknownProviderErrorIsReportedAsAGeneric401() throws Exception {
        handler.onAuthenticationFailure(request, response,
                oauth2Failure(OAuth2ErrorCodes.INVALID_GRANT));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body()).contains("\"code\":\"UNAUTHENTICATED\"")
                .contains("Google sign-in failed");
    }

    @Test
    void theProviderErrorDescriptionIsNeverEchoed() throws Exception {
        OAuth2AuthenticationException withDetail = new OAuth2AuthenticationException(
                new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
                        "state=super-secret-state&refresh_token=ya29.leaked", null));

        handler.onAuthenticationFailure(request, response, withDetail);

        assertThat(body())
                .doesNotContain("super-secret-state")
                .doesNotContain("refresh_token")
                .doesNotContain("ya29")
                .doesNotContain("OAuth2AuthenticationException")
                .doesNotContain("stackTrace");
    }

    @Test
    void anUnexpectedAuthenticationFailureIsAGeneric401() throws Exception {
        handler.onAuthenticationFailure(request, response,
                new BadCredentialsException("internal detail about the account"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body())
                .contains("\"code\":\"UNAUTHENTICATED\"")
                .doesNotContain("internal detail about the account")
                .doesNotContain("BadCredentials");
    }

    @Test
    void aServerSideAuthenticationFailureIsAGeneric500() throws Exception {
        handler.onAuthenticationFailure(request, response,
                new InternalAuthenticationServiceException("database unreachable",
                        new RuntimeException("caused by")));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(body())
                .contains("\"code\":\"INTERNAL_ERROR\"")
                .doesNotContain("database unreachable")
                .doesNotContain("caused by");
    }

    @Test
    void everyFailureResponseIsInCommonContractAndNeverCacheable() throws Exception {
        handler.onAuthenticationFailure(request, response, oauth2Failure(OAuth2ErrorCodes.ACCESS_DENIED));

        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(body())
                .contains("\"timestamp\":")
                .contains("\"error\":\"Unauthorized\"")
                .doesNotContain("Exception");
    }

    private OAuth2AuthenticationException oauth2Failure(String errorCode) {
        return new OAuth2AuthenticationException(new OAuth2Error(errorCode));
    }

    private String body() throws Exception {
        return response.getContentAsString(StandardCharsets.UTF_8);
    }
}
