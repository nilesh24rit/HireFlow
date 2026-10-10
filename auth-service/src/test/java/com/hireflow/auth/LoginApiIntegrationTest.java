package com.hireflow.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.hireflow.auth.entity.User;
import com.hireflow.auth.entity.UserRole;
import com.hireflow.auth.repository.UserRepository;
import com.hireflow.auth.security.JwtService;
import com.hireflow.auth.security.TestSigningKeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * End-to-end credential login through the real filter chain: the public
 * {@code POST /api/auth/login} endpoint exchanges verified credentials for a signed
 * access token, and rejections follow the common HireFlow error contract with a generic message
 * that cannot reveal whether an email is registered.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class LoginApiIntegrationTest {

    private static final String DATABASE_NAME = "hireflow_auth_login";
    private static final String RAW_PASSWORD = "Sup3r-Secret!";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName(DATABASE_NAME);

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Test-only JWT signing key; JwtService refuses to start without one.
        registry.add("hireflow.jwt.signing-key", () -> TestSigningKeys.VALID);
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

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

    @Test
    void loginIsPublicAndIssuesVerifiableAccessToken() throws Exception {
        User user = seedUser(RAW_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(user.getEmail(), RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String accessToken = com.jayway.jsonpath.JsonPath.read(body, "$.accessToken");
        assertThat(jwtService.validateAccessToken(accessToken)).isEqualTo(user.getId());
        assertThat(body).doesNotContain(RAW_PASSWORD).doesNotContain("$2a$");
    }

    @Test
    void rejectsWrongPasswordWithGeneric401() throws Exception {
        User user = seedUser(RAW_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(user.getEmail(), "definitely-wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    @Test
    void unknownEmailGetsExactlyTheSameResponseAsWrongPassword() throws Exception {
        User user = seedUser(RAW_PASSWORD);

        String unknownEmailBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("missing-" + UUID.randomUUID() + "@example.com", RAW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String wrongPasswordBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(user.getEmail(), "definitely-wrong-password")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // Identical apart from the echoed request path — no account-existence signal.
        assertThat(unknownEmailBody.replace("/api/auth/login", ""))
                .isEqualTo(wrongPasswordBody.replace("/api/auth/login", ""));
    }

    @Test
    void rejectsInvalidLoginPayloadWith400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\" \",\"password\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"password\":\"" + "x".repeat(73) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMalformedLoginJsonWith400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private User seedUser(String rawPassword) {
        User user = new User();
        user.setEmail("login-" + UUID.randomUUID() + "@example.com");
        user.setFirstName("Login");
        user.setLastName("Tester");
        user.setRole(UserRole.CANDIDATE);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return userRepository.saveAndFlush(user);
    }

    private String loginBody(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }
}
