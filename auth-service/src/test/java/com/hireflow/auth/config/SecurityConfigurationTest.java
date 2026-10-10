package com.hireflow.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.hireflow.auth.entity.User;
import com.hireflow.auth.entity.UserRole;
import com.hireflow.auth.repository.UserRepository;
import com.hireflow.auth.security.TestSigningKeys;

/**
 * Security tests at the filter-chain level, through Spring Security's own test support
 * ({@code springSecurity()}), complementing {@code AuthSecurityBaselineTest} which
 * observes the same rules over real HTTP. The authentication mechanism is stateless
 * bearer JWT:
 *
 * <ul>
 *   <li>the security configuration starts — the context boots with exactly the one
 *       explicit {@link SecurityFilterChain} of {@code SecurityConfiguration}, and every
 *       test below is executed through that chain;</li>
 *   <li>endpoint behaviour is preserved: requests carrying a valid bearer token pass,
 *       anonymous requests are rejected with the deliberate 401 error contract and the
 *       Bearer challenge;</li>
 *   <li>an invalid bearer token is rejected with the {@code invalid_token} challenge and
 *       can never be treated as anonymous success on a protected route;</li>
 *   <li>the CSRF policy holds: an authenticated POST with deliberately no CSRF token is
 *       accepted, because the service is stateless and header-authenticated;</li>
 *   <li>Swagger access matches the intended policy: anonymous {@code /v3/api-docs} is
 *       rejected (the authenticated side is asserted by {@code OpenApiDocumentationTest}).</li>
 * </ul>
 *
 * <p>No {@code formLogin()} or session login is part of the chain.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class SecurityConfigurationTest {

    private static final String DATABASE_NAME = "hireflow_auth";

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
    private com.hireflow.auth.security.JwtService jwtService;

    @Autowired
    private List<SecurityFilterChain> securityFilterChains;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void securityConfigurationStartsWithSingleExplicitChain() {
        // The application context boots with the explicit chain bean — nothing else may
        // silently replace or duplicate it.
        assertThat(securityFilterChains).hasSize(1);
    }

    @Test
    void getWithoutAuthenticationIsRejectedWith401Contract() throws Exception {
        User user = seedUser();

        mockMvc.perform(get("/api/users/{id}", user.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.path").value("/api/users/" + user.getId()));
    }

    @Test
    void getWithValidBearerTokenIsAccepted() throws Exception {
        User user = seedUser();
        String token = jwtService.generateAccessToken(user.getId(), user.getRole()).token();

        mockMvc.perform(get("/api/users/{id}", user.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.getEmail()));
    }

    @Test
    void getWithInvalidBearerTokenIsRejectedWithInvalidTokenChallenge() throws Exception {
        User user = seedUser();

        mockMvc.perform(get("/api/users/{id}", user.getId())
                        .header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("error=\"invalid_token\"")))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Invalid or expired authentication token"));
    }

    @Test
    void getWithAuthenticatedUserIsAccepted() throws Exception {
        User user = seedUser();

        mockMvc.perform(get("/api/users/{id}", user.getId()).with(user("security-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.getEmail()));
    }

    @Test
    void postWithAuthenticatedUserIsAcceptedWithoutCsrfToken() throws Exception {
        // CSRF policy: csrf() is deliberately NOT applied. The stateless, header-authenticated
        // service must accept this POST — the request the old masked-401 behaviour rejected.
        mockMvc.perform(post("/api/users")
                        .with(user("security-test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody()))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
    }

    @Test
    void postWithoutAuthenticationIsRejectedWith401Contract() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.path").value("/api/users"));
    }

    @Test
    void swaggerApiDocsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private User seedUser() {
        User user = new User();
        user.setEmail("security-config-" + UUID.randomUUID() + "@example.com");
        user.setFirstName("Security");
        user.setLastName("Config");
        user.setRole(UserRole.CANDIDATE);
        return userRepository.saveAndFlush(user);
    }

    private String createUserBody() {
        return """
                {"email":"security-config-create-%s@example.com","firstName":"Security","lastName":"Create","password":"Sup3r-Secret!","role":"CANDIDATE"}
                """.formatted(UUID.randomUUID());
    }
}
