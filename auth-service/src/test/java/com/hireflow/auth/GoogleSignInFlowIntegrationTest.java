package com.hireflow.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.hireflow.auth.config.GoogleOAuth2Configuration;
import com.hireflow.auth.entity.OAuth2Provider;
import com.hireflow.auth.entity.User;
import com.hireflow.auth.entity.UserRole;
import com.hireflow.auth.repository.UserOAuthIdentityRepository;
import com.hireflow.auth.repository.UserRepository;
import com.hireflow.auth.security.GoogleOAuth2SuccessHandler;
import com.hireflow.auth.security.JwtService;
import com.hireflow.auth.security.TestSigningKeys;
import com.hireflow.auth.service.GoogleIdentityService;
import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * The complete Google sign-in story against the real filter chain, the real database and the
 * real token machinery — with Google itself, and only Google itself, standing in.
 *
 * <p>Google's own legs of the flow (the authorization redirect, the code exchange, the
 * ID-token signature) are performed by Spring Security and would need a live Google account,
 * so they are not driven here. Everything this service is responsible for <i>after</i> Google
 * has spoken is: the callback runs through the real {@link GoogleOAuth2SuccessHandler} with a
 * genuinely OpenID-shaped principal, and the resulting handoff is redeemed over real HTTP.
 * That is enough to assert, end to end, the properties that actually matter:</p>
 *
 * <ul>
 *   <li>a first-time Google user is registered and can immediately call a protected endpoint
 *       with the token it obtained — the same endpoint, through the same filter chain, as a
 *       password login;</li>
 *   <li>a returning Google user lands on the same account rather than a second one, and a
 *       password user who links Google keeps both methods on one account;</li>
 *   <li>the refusals that guard against account takeover — an email that already belongs to
 *       someone else, and an unverified email — change nothing at all;</li>
 *   <li>neither a role nor the client secret can be obtained through Google, and no token
 *       ever appears in a callback response or a redirect.</li>
 * </ul>
 *
 * <p>Placeholder Google credentials are supplied so the OAuth2 endpoints exist and the real
 * chain is exercised; they are obviously fake and are never exchanged with Google.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class GoogleSignInFlowIntegrationTest {

    private static final String DATABASE_NAME = "hireflow_auth_google_flow";
    private static final String RAW_PASSWORD = "Sup3r-Secret!";

    /** Obviously fake credentials, activated so the OAuth2 endpoints exist; never exchanged. */
    static final String PLACEHOLDER_CLIENT_ID = "unit-test-only-google-client-id.apps.googleusercontent.com";
    static final String PLACEHOLDER_CLIENT_SECRET = "unit-test-only-google-client-secret-do-not-use";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName(DATABASE_NAME);

    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("hireflow.jwt.signing-key", () -> TestSigningKeys.VALID);
        registry.add(GoogleOAuth2Configuration.CLIENT_ID_PROPERTY, () -> PLACEHOLDER_CLIENT_ID);
        registry.add(GoogleOAuth2Configuration.CLIENT_SECRET_PROPERTY, () -> PLACEHOLDER_CLIENT_SECRET);
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private GoogleOAuth2SuccessHandler successHandler;

    @Autowired
    private GoogleIdentityService googleIdentityService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserOAuthIdentityRepository identityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    // ------------------------------------------------------------------
    // A complete successful sign-in
    // ------------------------------------------------------------------

    @Test
    void aFirstTimeGoogleUserIsRegisteredAndImmediatelyUsesTheIssuedToken() throws Exception {
        String subject = "flow-new-" + UUID.randomUUID();
        String email = subject + "@example.com";

        String accessToken = completeGoogleSignIn(verifiedClaims(subject, email));

        // The token is an ordinary HireFlow token: the API behind the same filter chain
        // accepts it exactly as it would one from password login.
        User created = userRepository.findByEmail(email).orElseThrow();
        mockMvc.perform(get("/api/users/" + created.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        assertThat(jwtService.validateAccessToken(accessToken)).isEqualTo(created.getId());
        assertThat(created.getRole()).isEqualTo(UserRole.CANDIDATE);
        // A Google-only account holds no password, so it can never password-login.
        assertThat(created.getPasswordHash()).isNull();
        assertThat(identityRepository.findByProviderAndProviderSubject(OAuth2Provider.GOOGLE, subject))
                .isPresent();
    }

    @Test
    void aGoogleTokenAndAPasswordTokenAreTheSameCredential() throws Exception {
        String subject = "flow-parity-" + UUID.randomUUID();
        String email = subject + "@example.com";
        User googleUser = signInNewGoogleUser(subject, email);
        String googleToken = completeGoogleSignIn(verifiedClaims(subject, email));

        User passwordUser = seedPasswordUser();
        String passwordToken = login(passwordUser);

        // Both open the identical endpoint through the identical chain; neither format is
        // special, and the tokens differ only in whose id they carry.
        mockMvc.perform(get("/api/users/" + googleUser.getId())
                        .header("Authorization", "Bearer " + googleToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/" + passwordUser.getId())
                        .header("Authorization", "Bearer " + passwordToken))
                .andExpect(status().isOk());

        assertThat(jwtService.validateAccessToken(googleToken)).isEqualTo(googleUser.getId());
        assertThat(jwtService.validateAccessToken(passwordToken)).isEqualTo(passwordUser.getId());
    }

    @Test
    void aHandoffCodeIsConsumedByItsFirstRedemption() throws Exception {
        String subject = "flow-single-use-" + UUID.randomUUID();
        MockHttpServletResponse callback = signIn(verifiedClaims(subject, subject + "@example.com"));
        String code = JsonPath.read(
                callback.getContentAsString(StandardCharsets.UTF_8), "$.handoffCode");

        // One sign-in, one handoff: the code is consumed by its first redemption, so the
        // callback response can never be replayed for a second token.
        assertThat(exchange(code, HttpStatus.OK)).contains("\"tokenType\":\"Bearer\"");
        assertThat(exchange(code, HttpStatus.UNAUTHORIZED))
                .contains("Invalid or expired sign-in handoff code");
    }

    private String exchange(String handoffCode, HttpStatus expected) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handoffCode\":\"%s\"}".formatted(handoffCode)))
                .andExpect(status().is(expected.value()))
                .andReturn();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void aReturningGoogleUserResolvesToTheSameAccount() throws Exception {
        String subject = "flow-returning-" + UUID.randomUUID();
        String email = subject + "@example.com";

        String firstToken = completeGoogleSignIn(verifiedClaims(subject, email));
        String secondToken = completeGoogleSignIn(verifiedClaims(subject, email));

        assertThat(jwtService.validateAccessToken(secondToken))
                .isEqualTo(jwtService.validateAccessToken(firstToken));
        assertThat(userRepository.findByEmail(email)).isPresent();
    }

    @Test
    void aPasswordUserWhoLinksGoogleKeepsBothMethodsOnOneAccount() throws Exception {
        User account = seedPasswordUser();
        String subject = "flow-both-" + UUID.randomUUID();

        // Linking is an authenticated act: the caller already owns the account.
        googleIdentityService.linkIdentity(account,
                com.hireflow.auth.dto.GoogleIdentity.fromClaims(
                        verifiedClaims(subject, account.getEmail())));

        // Google now signs into that same account, not a new one...
        assertThat(jwtService.validateAccessToken(
                completeGoogleSignIn(verifiedClaims(subject, account.getEmail()))))
                .isEqualTo(account.getId());
        // ...and the password still works for the very same account.
        assertThat(jwtService.validateAccessToken(login(account))).isEqualTo(account.getId());

        assertThat(identityRepository.findByProviderAndProviderSubject(OAuth2Provider.GOOGLE, subject))
                .isPresent()
                .get()
                .extracting(identity -> identity.getUserId())
                .isEqualTo(account.getId());
        // The account kept its password: linking added a method, it did not replace one.
        assertThat(userRepository.findById(account.getId()).orElseThrow().getPasswordHash())
                .isNotNull();
    }

    // ------------------------------------------------------------------
    // The refusals that guard against account takeover
    // ------------------------------------------------------------------

    @Test
    void anEmailAlreadyBelongingToAnAccountIsRefusedAndNothingChanges() throws Exception {
        User existing = seedPasswordUser();
        String unlinkedSubject = "flow-conflict-" + UUID.randomUUID();

        String body = signInExpecting(
                verifiedClaims(unlinkedSubject, existing.getEmail()), HttpStatus.CONFLICT);

        assertThat(body)
                .contains("\"code\":\"CONFLICT\"")
                .contains("link Google from your account settings")
                .doesNotContain(existing.getPasswordHash());

        // Nothing changed: the identity is not linked, the account is untouched, and no
        // second account appeared for the same email.
        assertThat(identityRepository.existsByProviderAndProviderSubject(
                OAuth2Provider.GOOGLE, unlinkedSubject)).isFalse();
        assertThat(userRepository.findByEmail(existing.getEmail())).isPresent();
    }

    @Test
    void anUnverifiedEmailIsRefusedAndNoAccountIsCreated() throws Exception {
        String subject = "flow-unverified-" + UUID.randomUUID();
        String email = subject + "@example.com";
        Map<String, Object> claims = verifiedClaims(subject, email);
        claims.put(StandardClaimNames.EMAIL_VERIFIED, Boolean.FALSE);

        signInExpecting(claims, HttpStatus.UNAUTHORIZED);

        // An unverified email is never trusted to identify or create a person.
        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void aDisabledAccountIsRefusedEvenWithAValidGoogleIdentity() throws Exception {
        String subject = "flow-disabled-" + UUID.randomUUID();
        String email = subject + "@example.com";
        User user = signInNewGoogleUser(subject, email);

        user.setEnabled(false);
        userRepository.saveAndFlush(user);

        signInExpecting(verifiedClaims(subject, email), HttpStatus.FORBIDDEN);
    }

    @Test
    void aRoleCanNeverBeObtainedThroughGoogle() throws Exception {
        String subject = "flow-privilege-" + UUID.randomUUID();
        String email = subject + "@example.com";
        Map<String, Object> claims = verifiedClaims(subject, email);
        // Privilege claims from the provider — or from anyone who shaped them — are
        // ignored outright; the role is fixed server-side at registration.
        claims.put("role", "ADMIN");
        claims.put("hd", "hireflow.example");
        claims.put("groups", List.of("admins"));

        signInNewGoogleUser(subject, email);

        assertThat(userRepository.findByEmail(email).orElseThrow().getRole())
                .isEqualTo(UserRole.CANDIDATE);
    }

    // ------------------------------------------------------------------
    // The callback over the real filter chain, and what must never leak
    // ------------------------------------------------------------------

    @Test
    void aCallbackWithNoPendingSignInIsRefusedInTheCommonErrorContract() throws Exception {
        mockMvc.perform(get("/login/oauth2/code/google")
                        .param("code", "unit-test-code")
                        .param("state", "unit-test-state"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/login/oauth2/code/google"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.handoffCode").doesNotExist());
    }

    @Test
    void theClientSecretAndClientIdNeverAppearInAnyResponse() throws Exception {
        MvcResult redirect = mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().isFound())
                .andReturn();

        String location = redirect.getResponse().getHeader("Location");
        assertThat(location).startsWith("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(location).doesNotContain(PLACEHOLDER_CLIENT_SECRET);
        assertThat(redirect.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain(PLACEHOLDER_CLIENT_SECRET);
    }

    @Test
    void aCompletedSignInPutsNoTokenInAnyResponseOrRedirect() throws Exception {
        String subject = "flow-no-leak-" + UUID.randomUUID();

        MockHttpServletResponse callback = signIn(
                verifiedClaims(subject, subject + "@example.com"));

        String body = callback.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .doesNotContain("accessToken")
                .doesNotContain("eyJ")
                .doesNotContain(PLACEHOLDER_CLIENT_SECRET);
        assertThat(callback.getHeader("Location")).isNull();
        assertThat(callback.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private record Callback(MockHttpServletResponse response) {

        String body() {
            try {
                return response.getContentAsString(StandardCharsets.UTF_8);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        String handoffCode() {
            return JsonPath.read(body(), "$.handoffCode");
        }
    }

    /** Runs the real success handler with an OpenID-shaped principal, as the chain would. */
    private MockHttpServletResponse signIn(Map<String, Object> claims) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(
                new MockHttpServletRequest("GET", "/login/oauth2/code/google"),
                response, googleAuthentication(claims));
        assertThat(response.getStatus())
                .as("sign-in should have completed; body was: %s",
                        response.getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(200);
        return response;
    }

    /** Runs a sign-in expected to be refused, returning the error contract body. */
    private String signInExpecting(Map<String, Object> claims, HttpStatus expected) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(
                new MockHttpServletRequest("GET", "/login/oauth2/code/google"),
                response, googleAuthentication(claims));

        assertThat(response.getStatus()).isEqualTo(expected.value());
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    /** A full sign-in followed by a real HTTP redemption of its handoff code. */
    private String completeGoogleSignIn(Map<String, Object> claims) throws Exception {
        Callback callback = new Callback(signIn(claims));

        MvcResult exchange = mockMvc.perform(post("/api/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handoffCode\":\"%s\"}".formatted(callback.handoffCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        return JsonPath.read(
                exchange.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.accessToken");
    }

    private User signInNewGoogleUser(String subject, String email) throws Exception {
        completeGoogleSignIn(verifiedClaims(subject, email));
        return userRepository.findByEmail(email).orElseThrow();
    }

    private OAuth2AuthenticationToken googleAuthentication(Map<String, Object> claims) {
        OidcIdToken idToken = new OidcIdToken("google-id-token", Instant.now(),
                Instant.now().plusSeconds(300), claims);
        OidcUser oidcUser = new DefaultOidcUser(List.of(new OidcUserAuthority(idToken)),
                idToken, StandardClaimNames.SUB);
        return new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "google");
    }

    private Map<String, Object> verifiedClaims(String subject, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(StandardClaimNames.SUB, subject);
        claims.put(StandardClaimNames.EMAIL, email);
        claims.put(StandardClaimNames.EMAIL_VERIFIED, Boolean.TRUE);
        claims.put(StandardClaimNames.GIVEN_NAME, "Google");
        claims.put(StandardClaimNames.FAMILY_NAME, "User");
        return claims;
    }

    private User seedPasswordUser() {
        User user = new User();
        user.setEmail("flow-password-" + UUID.randomUUID() + "@example.com");
        user.setFirstName("Password");
        user.setLastName("User");
        user.setRole(UserRole.CANDIDATE);
        user.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        return userRepository.saveAndFlush(user);
    }

    private String login(User user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(user.getEmail(), RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.accessToken");
    }
}
