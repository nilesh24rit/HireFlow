package com.hireflow.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * Verifies the operator-supplied redirect base URL used when auth-service is reached
 * through the API gateway: {@code HIREFLOW_OAUTH_REDIRECT_URI} pins the {@code redirect_uri}
 * sent to Google to {@code <base>/login/oauth2/code/google}, so the browser is sent back
 * through the gateway rather than directly to the service's internal address. The value is
 * trusted operator configuration, not a client-supplied forwarded header.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class GoogleOAuth2RedirectUriConfigurationTest {

    private static final String DATABASE_NAME = "hireflow_auth_oauth2_redirect";

    static final String PLACEHOLDER_CLIENT_ID = "unit-test-only-google-client-id.apps.googleusercontent.com";
    static final String PLACEHOLDER_CLIENT_SECRET = "unit-test-only-google-client-secret";

    /** Simulated public gateway address the browser talks to. */
    static final String PUBLIC_BASE_URL = "http://localhost:8080";

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
        registry.add(GoogleOAuth2Configuration.REDIRECT_BASE_URL_PROPERTY, () -> PUBLIC_BASE_URL + "/");
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
    void registrationUsesPublicGatewayBaseForCallback() {
        assertThat(clientRegistrationRepository.findByRegistrationId("google").getRedirectUri())
                .isEqualTo(PUBLIC_BASE_URL + "/login/oauth2/code/google");
    }

    @Test
    void authorizationRedirectCarriesThePinnedCallback() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().isFound())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        assertThat(queryParameter(location, "redirect_uri"))
                .isEqualTo(PUBLIC_BASE_URL + "/login/oauth2/code/google");
        // The pinned value is used verbatim; no forwarded header of the incoming request
        // can override it.
        assertThat(location).doesNotContain("X-Forwarded");
    }

    private String queryParameter(String location, String name) {
        String query = URI.create(location).getRawQuery();
        for (String pair : query.split("&")) {
            if (pair.startsWith(name + "=")) {
                return URLDecoder.decode(pair.substring(name.length() + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
