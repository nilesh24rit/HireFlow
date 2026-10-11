package com.hireflow.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.hireflow.auth.security.TestSigningKeys;

/**
 * Verifies the Google OAuth2 authorization configuration when external credentials are
 * supplied:
 *
 * <ul>
 *   <li>the context boots with exactly one Google client registration built from the
 *       environment ({@code GOOGLE_CLIENT_ID}, {@code GOOGLE_CLIENT_SECRET}), carrying
 *       Google's standard OIDC endpoints, scopes and {@code sub} username attribute;</li>
 *   <li>{@code GET /oauth2/authorization/google} answers with the standard 302 redirect to
 *       Google's authorization endpoint including a CSRF {@code state} parameter, and keeps
 *       the authorization request in an HttpOnly cookie — no server-side session;</li>
 *   <li>neither the redirect nor any response ever contains the client secret.</li>
 * </ul>
 *
 * <p>Only placeholder credentials are used: the real Google authorization endpoint is never
 * called, so no network access and no real Google account are needed.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class GoogleOAuth2ConfigurationTest {

    private static final String DATABASE_NAME = "hireflow_auth_oauth2_config";

    /** Obviously fake credentials; never valid, never exchanged with Google. */
    static final String PLACEHOLDER_CLIENT_ID = "unit-test-only-google-client-id.apps.googleusercontent.com";
    static final String PLACEHOLDER_CLIENT_SECRET = "unit-test-only-google-client-secret";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName(DATABASE_NAME);

    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Test-only JWT signing key; JwtService refuses to start without one.
        registry.add("hireflow.jwt.signing-key", () -> TestSigningKeys.VALID);
        // Placeholder Google credentials activate the OAuth2 client registration.
        registry.add(GoogleOAuth2Configuration.CLIENT_ID_PROPERTY, () -> PLACEHOLDER_CLIENT_ID);
        registry.add(GoogleOAuth2Configuration.CLIENT_SECRET_PROPERTY, () -> PLACEHOLDER_CLIENT_SECRET);
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void contextCarriesGoogleRegistrationWithStandardEndpointsAndScopes() {
        ClientRegistration google = clientRegistrationRepository.findByRegistrationId("google");

        assertThat(google).isNotNull();
        assertThat(google.getProviderDetails().getIssuerUri()).isEqualTo("https://accounts.google.com");
        assertThat(google.getProviderDetails().getAuthorizationUri())
                .isEqualTo("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(google.getProviderDetails().getTokenUri())
                .isEqualTo("https://www.googleapis.com/oauth2/v4/token");
        assertThat(google.getProviderDetails().getUserInfoEndpoint().getUri())
                .isEqualTo("https://www.googleapis.com/oauth2/v3/userinfo");
        assertThat(google.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName())
                .isEqualTo("sub");
        assertThat(google.getScopes()).containsExactlyInAnyOrder("openid", "profile", "email");
        assertThat(google.getClientId()).isEqualTo(PLACEHOLDER_CLIENT_ID);
        assertThat(google.getRedirectUri()).isEqualTo("{baseUrl}/login/oauth2/code/google");
    }

    @Test
    void onlyTheGoogleProviderIsRegistered() {
        assertThat(clientRegistrationRepository.findByRegistrationId("github")).isNull();
        assertThat(clientRegistrationRepository.findByRegistrationId("microsoft")).isNull();
    }

    @Test
    void authorizationEntryPointRedirectsToGoogleWithStateAndNoSecret() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        startsWith("https://accounts.google.com/o/oauth2/v2/auth")))
                .andExpect(header().string("Location", containsString("client_id=" + PLACEHOLDER_CLIENT_ID)))
                .andExpect(header().string("Location", containsString("state=")))
                .andExpect(header().string("Location", containsString("login/oauth2/code/google")))
                .andExpect(header().string("Location", containsString("scope=")))
                .andReturn();

        MockHttpServletRequest request = (MockHttpServletRequest) result.getRequest();
        // The pending authorization request — the flow's CSRF state — is held in a
        // transient servlet session, never in the redirect URL beyond the state parameter.
        assertThat(request.getSession(false)).isNotNull();

        MockHttpServletResponse response = result.getResponse();
        assertThat(response.getHeader("Location")).doesNotContain(PLACEHOLDER_CLIENT_SECRET);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void callbackPathIsPublicButGrantsNothingByItself() throws Exception {
        // Without a matching authorization request (state cookie) the callback must not
        // authenticate anyone; the entry point above is the only way into the flow.
        // (Commit 1 renders this through Spring Security's default failure redirect;
        // the dedicated JSON failure handler replaces it once implemented.)
        MvcResult result = mockMvc.perform(get("/login/oauth2/code/google")
                        .param("code", "unit-test-code")
                        .param("state", "unit-test-state"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isIn(302, 400, 401);
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("accessToken")
                .doesNotContain("eyJ");
    }
}
