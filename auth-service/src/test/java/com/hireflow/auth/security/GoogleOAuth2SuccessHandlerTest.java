package com.hireflow.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import com.hireflow.auth.dto.GoogleIdentity;
import com.hireflow.auth.entity.User;
import com.hireflow.auth.entity.UserRole;
import com.hireflow.auth.exception.OAuth2IdentityException;
import com.hireflow.auth.exception.OAuth2IdentityException.Reason;
import com.hireflow.auth.service.GoogleIdentityService;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies how a Google sign-in finishes, using a real {@link JwtService} and a real
 * {@link OAuthHandoffService} so the assertions describe what a client would actually
 * observe:
 *
 * <ul>
 *   <li>a completed sign-in hands back a single-use handoff code and <b>never</b> the JWT
 *       itself — the response body is inspected for token material, not just for a field
 *       name;</li>
 *   <li>redeeming that code yields a genuinely signed token that the service's own
 *       validator accepts, i.e. the very token password login produces;</li>
 *   <li>each deliberate refusal — disabled account, unverified email, an email already
 *       belonging to someone else, a missing subject — surfaces with its own status and
 *       says nothing about internals;</li>
 *   <li>the claims that gate identity are re-checked here, so a malformed or unverified
 *       identity never reaches the database;</li>
 *   <li>anything unexpected becomes a generic 500, and the transient OAuth2 principal is
 *       cleared so no authority outlives the request that proved it.</li>
 * </ul>
 */
class GoogleOAuth2SuccessHandlerTest {

    private static final String CALLBACK_PATH = "/login/oauth2/code/google";

    private GoogleIdentityService googleIdentityService;
    private JwtService jwtService;
    private OAuthHandoffService handoffService;
    private GoogleOAuth2SuccessHandler handler;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        googleIdentityService = mock(GoogleIdentityService.class);
        jwtService = new JwtService(TestSigningKeys.VALID, 3600, "hireflow-auth");
        handoffService = new OAuthHandoffService(60);
        handler = new GoogleOAuth2SuccessHandler(googleIdentityService, jwtService,
                handoffService, new OAuth2ResponseWriter(JsonMapper.builder().build()));

        request = new MockHttpServletRequest("GET", CALLBACK_PATH);
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // Success: a handoff, never the token
    // ------------------------------------------------------------------

    @Test
    void completedSignInReturnsAOneTimeHandoffAndNeverTheToken() throws Exception {
        User user = enabledUser();
        when(googleIdentityService.authenticateWithGoogle(any()))
                .thenReturn(user);

        handler.onAuthenticationSuccess(request, response, googleAuthentication(verifiedClaims()));

        assertThat(response.getStatus()).isEqualTo(200);
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"handoffCode\":").contains("\"expiresIn\":60");
        // The whole point: no token material of any kind leaves the callback.
        assertThat(body).doesNotContain("accessToken").doesNotContain("eyJ");

        verify(googleIdentityService).authenticateWithGoogle(new GoogleIdentity(
                "google-sub-123", "jane@example.com", "Jane", "Doe"));
    }

    @Test
    void redeemingTheHandoffYieldsTheOrdinarySignedAccessToken() throws Exception {
        User user = enabledUser();
        when(googleIdentityService.authenticateWithGoogle(any())).thenReturn(user);

        handler.onAuthenticationSuccess(request, response, googleAuthentication(verifiedClaims()));

        String code = handoffCode(response);
        OAuthHandoffService.Handoff handoff = handoffService.redeem(code).orElseThrow();

        assertThat(handoff.userId()).isEqualTo(user.getId());
        assertThat(jwtService.validateAccessToken(handoff.accessToken().token()))
                .isEqualTo(user.getId());
    }

    @Test
    void theHandoffCodeIsSingleUse() throws Exception {
        when(googleIdentityService.authenticateWithGoogle(any())).thenReturn(enabledUser());

        handler.onAuthenticationSuccess(request, response, googleAuthentication(verifiedClaims()));
        String code = handoffCode(response);

        assertThat(handoffService.redeem(code)).isPresent();
        assertThat(handoffService.redeem(code)).isEmpty();
    }

    @Test
    void theCallbackResponseIsNeverCacheable() throws Exception {
        when(googleIdentityService.authenticateWithGoogle(any())).thenReturn(enabledUser());

        handler.onAuthenticationSuccess(request, response, googleAuthentication(verifiedClaims()));

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void theTransientOAuth2PrincipalIsClearedAfterCompletion() throws Exception {
        when(googleIdentityService.authenticateWithGoogle(any())).thenReturn(enabledUser());
        SecurityContextHolder.getContext()
                .setAuthentication(googleAuthentication(verifiedClaims()));

        handler.onAuthenticationSuccess(request, response, googleAuthentication(verifiedClaims()));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ------------------------------------------------------------------
    // Deliberate refusals, each with its own status
    // ------------------------------------------------------------------

    @Test
    void anEmailAlreadyBelongingToAnAccountIsRefusedWith409() throws Exception {
        when(googleIdentityService.authenticateWithGoogle(any()))
                .thenThrow(new OAuth2IdentityException(Reason.EMAIL_ALREADY_REGISTERED));

        handler.onAuthenticationSuccess(request, response, googleAuthentication(verifiedClaims()));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"status\":409")
                .contains("\"code\":\"CONFLICT\"")
                .contains("link Google from your account settings");
    }

    @Test
    void anUnverifiedEmailIsRefusedWith401() throws Exception {
        when(googleIdentityService.authenticateWithGoogle(any()))
                .thenThrow(new OAuth2IdentityException(Reason.EMAIL_NOT_VERIFIED));

        handler.onAuthenticationSuccess(request, response, googleAuthentication(verifiedClaims()));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"code\":\"UNAUTHENTICATED\"");
    }

    @Test
    void aDisabledAccountIsRefusedWith403() throws Exception {
        when(googleIdentityService.authenticateWithGoogle(any()))
                .thenThrow(new OAuth2IdentityException(Reason.ACCOUNT_DISABLED));

        handler.onAuthenticationSuccess(request, response, googleAuthentication(verifiedClaims()));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"code\":\"FORBIDDEN\"");
    }

    @Test
    void aMissingSubjectIsRefusedWith400() throws Exception {
        when(googleIdentityService.authenticateWithGoogle(any()))
                .thenThrow(new OAuth2IdentityException(Reason.MISSING_SUBJECT));

        handler.onAuthenticationSuccess(request, response, googleAuthentication(verifiedClaims()));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"code\":\"INVALID_REQUEST\"");
    }

    // ------------------------------------------------------------------
    // Claim gates: refused before any lookup happens
    // ------------------------------------------------------------------

    @Test
    void aClaimSetWithoutASubjectNeverReachesTheDatabase() throws Exception {
        // An OpenID user can exist without a sub claim when the provider names accounts by
        // something else. Even then this service refuses rather than guessing an identity:
        // the subject is the only key an account mapping may be built on.
        Map<String, Object> claims = verifiedClaims();
        claims.remove(StandardClaimNames.SUB);
        OidcIdToken idToken = new OidcIdToken("google-id-token", Instant.now(),
                Instant.now().plusSeconds(300), claims);
        OidcUser subjectless = new DefaultOidcUser(List.of(new OidcUserAuthority(idToken)),
                idToken, StandardClaimNames.EMAIL);

        handler.onAuthenticationSuccess(request, response,
                new OAuth2AuthenticationToken(subjectless, subjectless.getAuthorities(), "google"));

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(googleIdentityService);
    }

    @Test
    void anUnverifiedEmailClaimNeverReachesTheDatabase() throws Exception {
        Map<String, Object> claims = verifiedClaims();
        claims.put(StandardClaimNames.EMAIL_VERIFIED, Boolean.FALSE);

        handler.onAuthenticationSuccess(request, response, googleAuthentication(claims));

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(googleIdentityService);
    }

    @Test
    void aPrincipalThatIsNotAnOpenIdUserIsRefused() throws Exception {
        handler.onAuthenticationSuccess(request, response,
                new UsernamePasswordAuthenticationToken("not-an-oidc-user", "n/a"));

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(googleIdentityService);
    }

    // ------------------------------------------------------------------
    // Anything unexpected stays generic
    // ------------------------------------------------------------------

    @Test
    void anUnexpectedFailureBecomesAGeneric500WithNoInternals() throws Exception {
        when(googleIdentityService.authenticateWithGoogle(any()))
                .thenThrow(new IllegalStateException("connection refused to 10.0.0.7:5432"));

        handler.onAuthenticationSuccess(request, response, googleAuthentication(verifiedClaims()));

        assertThat(response.getStatus()).isEqualTo(500);
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"code\":\"INTERNAL_ERROR\"")
                .contains("An unexpected error occurred");
        assertThat(body)
                .doesNotContain("10.0.0.7")
                .doesNotContain("IllegalState")
                .doesNotContain("Exception")
                .doesNotContain("stackTrace");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Map<String, Object> verifiedClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put(StandardClaimNames.SUB, "google-sub-123");
        claims.put(StandardClaimNames.EMAIL, "jane@example.com");
        claims.put(StandardClaimNames.EMAIL_VERIFIED, Boolean.TRUE);
        claims.put(StandardClaimNames.GIVEN_NAME, "Jane");
        claims.put(StandardClaimNames.FAMILY_NAME, "Doe");
        return claims;
    }

    private OAuth2AuthenticationToken googleAuthentication(Map<String, Object> claims) {
        OidcIdToken idToken = new OidcIdToken("google-id-token", Instant.now(),
                Instant.now().plusSeconds(300), claims);
        OidcUser oidcUser = new DefaultOidcUser(List.of(new OidcUserAuthority(idToken)),
                idToken, StandardClaimNames.SUB);
        return new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "google");
    }

    private String handoffCode(MockHttpServletResponse response) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
                response.getContentAsString(StandardCharsets.UTF_8), "$.handoffCode");
    }

    /** A detached user whose id the real {@link JwtService} will embed as the subject. */
    private User enabledUser() {
        User user = new User();
        user.setEmail("jane@example.com");
        user.setFirstName("Jane");
        user.setLastName("Doe");
        user.setRole(UserRole.CANDIDATE);
        user.setEnabled(true);
        assignId(user, UUID.randomUUID());
        return user;
    }

    private static void assignId(User user, UUID id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Test could not assign the user id", ex);
        }
    }
}
