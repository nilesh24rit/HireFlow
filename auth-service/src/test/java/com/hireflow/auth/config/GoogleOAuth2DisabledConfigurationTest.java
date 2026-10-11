package com.hireflow.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.hireflow.auth.security.TestSigningKeys;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/**
 * Verifies the deliberate behaviour when no Google credentials are configured — the
 * default state of every environment that has not opted into Google sign-in:
 *
 * <ul>
 *   <li>the context boots cleanly and contributes no
 *       {@link ClientRegistrationRepository} at all, so nothing can be half-configured;</li>
 *   <li>the OAuth2 entry point does not exist (404 in the common error contract) instead
 *       of redirecting anywhere or leaking a configuration problem;</li>
 *   <li>every existing rule — bearer authentication, the login endpoint — is unchanged.</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class GoogleOAuth2DisabledConfigurationTest {

    private static final String DATABASE_NAME = "hireflow_auth_oauth2_off";

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
        // Deliberately no GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET.
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ApplicationContext applicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void contextBootsWithoutAnyOAuth2ClientRegistration() {
        assertThat(applicationContext.getBeanNamesForType(ClientRegistrationRepository.class))
                .isEmpty();
    }

    @Test
    void googleEntryPointDoesNotExistWithoutCredentials() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void googleCallbackDoesNotExistWithoutCredentials() throws Exception {
        mockMvc.perform(get("/login/oauth2/code/google")
                        .param("code", "unit-test-code")
                        .param("state", "unit-test-state"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void protectedEndpointsStillRequireBearerToken() throws Exception {
        mockMvc.perform(get("/api/users/" + java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
