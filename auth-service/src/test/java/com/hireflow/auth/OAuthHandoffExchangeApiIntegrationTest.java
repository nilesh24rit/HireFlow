package com.hireflow.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
import com.hireflow.auth.security.IssuedAccessToken;
import com.hireflow.auth.security.JwtService;
import com.hireflow.auth.security.OAuthHandoffService;
import com.hireflow.auth.security.TestSigningKeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Exercises {@code POST /api/auth/oauth2/token} — the backend completion step of Google
 * sign-in — through the real filter chain.
 *
 * <p>The callback that mints the code cannot be driven here without Google's cooperation,
 * so the code is issued exactly as {@code GoogleOAuth2SuccessHandler} issues it, through
 * the same {@link OAuthHandoffService} bean. What is verified is the contract a client
 * depends on: the public exchange turns a single-use code into the very token the callback
 * signed, refuses a replay or an unknown code with the ordinary 401 contract, and needs no
 * bearer token of its own — the caller holds none yet.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class OAuthHandoffExchangeApiIntegrationTest {

    private static final String DATABASE_NAME = "hireflow_auth_oauth_handoff";

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
        // Deliberately no GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET: this endpoint is the
        // handoff exchange, not the OAuth2 dance, and must work on its own.
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private OAuthHandoffService handoffService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void aFreshHandoffCodeExchangesForTheVeryTokenTheCallbackSigned() throws Exception {
        User user = seedUser();
        IssuedAccessToken accessToken = jwtService.generateAccessToken(user.getId(), user.getRole());
        String code = handoffService.issue(user.getId(), accessToken);

        MvcResult result = mockMvc.perform(post("/api/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handoffBody(code)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String returned = com.jayway.jsonpath.JsonPath.read(body, "$.accessToken");
        // It is the identical token the callback signed — the same JwtService, not a
        // second token format invented for Google.
        assertThat(returned).isEqualTo(accessToken.token());
        assertThat(jwtService.validateAccessToken(returned)).isEqualTo(user.getId());
    }

    @Test
    void theSameHandoffCodeCannotBeExchangedTwice() throws Exception {
        User user = seedUser();
        String code = handoffService.issue(user.getId(),
                jwtService.generateAccessToken(user.getId(), user.getRole()));

        mockMvc.perform(post("/api/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handoffBody(code)))
                .andExpect(status().isOk());

        // Redemption consumed the code, so the replay is refused like any bad credential.
        mockMvc.perform(post("/api/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handoffBody(code)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Invalid or expired sign-in handoff code"))
                .andExpect(jsonPath("$.path").value("/api/auth/oauth2/token"));
    }

    @Test
    void anUnknownHandoffCodeIsRejectedWithTheGeneric401Contract() throws Exception {
        mockMvc.perform(post("/api/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handoffBody("not-a-real-handoff-code")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Invalid or expired sign-in handoff code"));
    }

    @Test
    void aWellFormedButUnissuedCodeIsRejectedTheSameWay() throws Exception {
        User user = seedUser();
        handoffService.issue(user.getId(),
                jwtService.generateAccessToken(user.getId(), user.getRole()));

        // Right shape, right length, never issued: indistinguishable from a wrong code.
        mockMvc.perform(post("/api/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handoffBody("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aBlankHandoffCodeIsRejectedWithA400() throws Exception {
        mockMvc.perform(post("/api/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handoffCode\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.handoffCode").exists());
    }

    @Test
    void theExchangeNeedsNoBearerTokenOfItsOwn() throws Exception {
        User user = seedUser();
        String code = handoffService.issue(user.getId(),
                jwtService.generateAccessToken(user.getId(), user.getRole()));

        // No Authorization header anywhere: the single-use code is the only credential,
        // which is precisely why the client can complete a Google sign-in it started
        // without ever holding a token.
        assertThat(mockMvc.perform(post("/api/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handoffBody(code)))
                .andReturn().getResponse().getStatus()).isEqualTo(200);

        // Bearer authentication itself is untouched: a protected route still demands one.
        mockMvc.perform(get("/api/users/" + user.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theHandoffCodeNeverAppearsInTheReturnedBody() throws Exception {
        User user = seedUser();
        String code = handoffService.issue(user.getId(),
                jwtService.generateAccessToken(user.getId(), user.getRole()));

        MvcResult result = mockMvc.perform(post("/api/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handoffBody(code)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain(code)
                .doesNotContain("handoffCode")
                .doesNotContain("$2a$");
    }

    private User seedUser() {
        User user = new User();
        user.setEmail("oauth-handoff-" + UUID.randomUUID() + "@example.com");
        user.setFirstName("Handoff");
        user.setLastName("Tester");
        user.setRole(UserRole.CANDIDATE);
        return userRepository.saveAndFlush(user);
    }

    private String handoffBody(String code) {
        return "{\"handoffCode\":\"%s\"}".formatted(code);
    }
}
