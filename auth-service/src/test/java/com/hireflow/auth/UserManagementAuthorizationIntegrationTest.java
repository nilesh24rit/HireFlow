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
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * The auth-service side of the Step 15 permission matrix, executed end to end through the
 * real filter chain: tokens are minted by the real {@link JwtService} (or obtained from the
 * real login endpoint), the filter turns them into a principal plus one role authority, and
 * the {@code @PreAuthorize} rules on {@code UserService} decide. Nothing here is asserted
 * against a mocked annotation — every refusal and every allowance is observed on live,
 * persisted data.
 *
 * <p>What is pinned down:</p>
 *
 * <ul>
 *   <li><b>Role rules</b> — role assignment, directory lookup and deletion are ADMIN-only;
 *       a CANDIDATE or RECRUITER token receives the 403 contract (never 401, never a
 *       challenge), and a refused attempt leaves the database untouched.</li>
 *   <li><b>Ownership</b> — a user reads and edits their own persisted record and nobody
 *       else's; the comparison is against the principal the token authenticated, not
 *       against anything the request body claims.</li>
 *   <li><b>No self-privilege escalation</b> — neither the create payload nor the profile
 *       payload can carry a role: {@code role} on create is ignored for every caller
 *       (accounts are born CANDIDATE) and ignored on self-update, so the only way to gain
 *       a role is the ADMIN-only endpoint.</li>
 *   <li><b>Stale roles and disabled accounts</b> — a role change persists immediately and
 *       shows up in the next token, while tokens already issued keep the role they were
 *       signed with until they expire: the token TTL is the invalidation window, there is
 *       no session or refresh token to revoke. A disabled account cannot obtain a new
 *       token (generic 401, no account-state leak); its outstanding token likewise lives
 *       only until expiry.</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class UserManagementAuthorizationIntegrationTest {

    private static final String DATABASE_NAME = "hireflow_auth_rbac";
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

    // ------------------------------------------------------------------
    // Role management is ADMIN-only
    // ------------------------------------------------------------------

    @Test
    void candidateCannotAssignARole() throws Exception {
        User candidate = seedUser(UserRole.CANDIDATE);
        User target = seedUser(UserRole.CANDIDATE);

        mockMvc.perform(put("/api/users/{id}/role", target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}")
                        .header("Authorization", bearerFor(candidate)))
                // Authentication succeeded, authorization failed: the 403 contract, with
                // no Bearer challenge (that belongs to 401) and no credential reflection.
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(jsonPath("$.path").value("/api/users/" + target.getId() + "/role"));

        // A refused attempt must not have changed anything.
        assertThat(userRepository.findById(target.getId()).orElseThrow().getRole())
                .isEqualTo(UserRole.CANDIDATE);
    }

    @Test
    void recruiterCannotAssignARole() throws Exception {
        User recruiter = seedUser(UserRole.RECRUITER);
        User target = seedUser(UserRole.CANDIDATE);

        mockMvc.perform(put("/api/users/{id}/role", target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"RECRUITER\"}")
                        .header("Authorization", bearerFor(recruiter)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(userRepository.findById(target.getId()).orElseThrow().getRole())
                .isEqualTo(UserRole.CANDIDATE);
    }

    @Test
    void administratorAssignsARoleAndItIsPersisted() throws Exception {
        User administrator = seedUser(UserRole.ADMIN);
        User target = seedUser(UserRole.CANDIDATE);

        mockMvc.perform(put("/api/users/{id}/role", target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"RECRUITER\"}")
                        .header("Authorization", bearerFor(administrator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.getId().toString()))
                .andExpect(jsonPath("$.role").value("RECRUITER"));

        // The authority change is in the database — the source every future token reads.
        assertThat(userRepository.findById(target.getId()).orElseThrow().getRole())
                .isEqualTo(UserRole.RECRUITER);
    }

    @Test
    void roleAssignmentRejectsAMissingRole() throws Exception {
        User administrator = seedUser(UserRole.ADMIN);
        User target = seedUser(UserRole.CANDIDATE);

        mockMvc.perform(put("/api/users/{id}/role", target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", bearerFor(administrator)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.role").exists());
    }

    @Test
    void roleAssignmentRejectsAnUnknownRoleValue() throws Exception {
        User administrator = seedUser(UserRole.ADMIN);
        User target = seedUser(UserRole.CANDIDATE);

        mockMvc.perform(put("/api/users/{id}/role", target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SUPER_ADMIN\"}")
                        .header("Authorization", bearerFor(administrator)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Unknown UserRole value 'SUPER_ADMIN'"));

        assertThat(userRepository.findById(target.getId()).orElseThrow().getRole())
                .isEqualTo(UserRole.CANDIDATE);
    }

    // ------------------------------------------------------------------
    // No self-privilege escalation through any other payload
    // ------------------------------------------------------------------

    @Test
    void createIgnoresAnyRoleInThePayloadForEveryCaller() throws Exception {
        User candidate = seedUser(UserRole.CANDIDATE);
        User administrator = seedUser(UserRole.ADMIN);

        // Even an administrator cannot grant a role at creation: the payload has no such
        // field, so whatever is sent is dropped and the default is assigned. The one
        // escalation path stays the single ADMIN-only endpoint.
        String asCandidate = createBodyWithRole(
                "escalate-candidate-" + UUID.randomUUID() + "@example.com", "ADMIN");
        String asAdmin = createBodyWithRole(
                "escalate-admin-" + UUID.randomUUID() + "@example.com", "ADMIN");

        MvcResult candidateCreate = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asCandidate)
                        .header("Authorization", bearerFor(candidate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CANDIDATE"))
                .andReturn();
        MvcResult adminCreate = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asAdmin)
                        .header("Authorization", bearerFor(administrator)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CANDIDATE"))
                .andReturn();

        assertCreatedRoleIsPersistedAsCandidate(candidateCreate, "escalate-candidate");
        assertCreatedRoleIsPersistedAsCandidate(adminCreate, "escalate-admin");
    }

    @Test
    void selfProfileUpdateCannotChangeRoleOrEmail() throws Exception {
        User candidate = seedUser(UserRole.CANDIDATE);

        mockMvc.perform(put("/api/users/{id}", candidate.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Janet\",\"lastName\":\"Smith\","
                                + "\"email\":\"hijacked@example.com\",\"role\":\"ADMIN\"}")
                        .header("Authorization", bearerFor(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Janet"))
                .andExpect(jsonPath("$.role").value("CANDIDATE"))
                .andExpect(jsonPath("$.email").value(candidate.getEmail()));

        User reloaded = userRepository.findById(candidate.getId()).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(UserRole.CANDIDATE);
        assertThat(reloaded.getEmail()).isEqualTo(candidate.getEmail());
    }

    // ------------------------------------------------------------------
    // Ownership against persisted data
    // ------------------------------------------------------------------

    @Test
    void candidateReadsOwnRecord() throws Exception {
        User candidate = seedUser(UserRole.CANDIDATE);

        mockMvc.perform(get("/api/users/{id}", candidate.getId())
                        .header("Authorization", bearerFor(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(candidate.getId().toString()))
                .andExpect(jsonPath("$.email").value(candidate.getEmail()));
    }

    @Test
    void candidateCannotReadAnotherUsersRecord() throws Exception {
        User candidate = seedUser(UserRole.CANDIDATE);
        User stranger = seedUser(UserRole.CANDIDATE);

        mockMvc.perform(get("/api/users/{id}", stranger.getId())
                        .header("Authorization", bearerFor(candidate)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void candidateCannotUpdateAnotherUsersRecord() throws Exception {
        User candidate = seedUser(UserRole.CANDIDATE);
        User stranger = seedUser(UserRole.CANDIDATE);

        mockMvc.perform(put("/api/users/{id}", stranger.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Mine\",\"lastName\":\"Now\"}")
                        .header("Authorization", bearerFor(candidate)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(userRepository.findById(stranger.getId()).orElseThrow().getFirstName())
                .isEqualTo("Jane");
    }

    @Test
    void candidateCannotDeleteAUserAndAdministratorCan() throws Exception {
        User candidate = seedUser(UserRole.CANDIDATE);
        User administrator = seedUser(UserRole.ADMIN);
        User target = seedUser(UserRole.CANDIDATE);

        mockMvc.perform(delete("/api/users/{id}", target.getId())
                        .header("Authorization", bearerFor(candidate)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(userRepository.findById(target.getId())).isPresent();

        mockMvc.perform(delete("/api/users/{id}", target.getId())
                        .header("Authorization", bearerFor(administrator)))
                .andExpect(status().isNoContent());
        assertThat(userRepository.findById(target.getId())).isEmpty();
    }

    @Test
    void emailLookupIsAdministratorOnly() throws Exception {
        User candidate = seedUser(UserRole.CANDIDATE);
        User recruiter = seedUser(UserRole.RECRUITER);
        User administrator = seedUser(UserRole.ADMIN);

        // Even your OWN email may not be resolved through the directory endpoint: the
        // rule is the role, because ownership cannot be derived from an email path.
        mockMvc.perform(get("/api/users/email/{email}", candidate.getEmail())
                        .header("Authorization", bearerFor(candidate)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/users/email/{email}", candidate.getEmail())
                        .header("Authorization", bearerFor(recruiter)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/users/email/{email}", candidate.getEmail())
                        .header("Authorization", bearerFor(administrator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(candidate.getId().toString()));
    }

    // ------------------------------------------------------------------
    // 401 for credentials, 403 for authorization — never crossed
    // ------------------------------------------------------------------

    @Test
    void roleEndpointWithoutTokenIsA401NotA403() throws Exception {
        User target = seedUser(UserRole.CANDIDATE);

        mockMvc.perform(put("/api/users/{id}/role", target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/api/users/{id}", target.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ------------------------------------------------------------------
    // Stale roles and disabled accounts: the token TTL is the window
    // ------------------------------------------------------------------

    @Test
    void roleChangeAppliesToNewTokensImmediatelyWhileIssuedTokensKeepTheirRoleUntilExpiry()
            throws Exception {
        User promoter = seedUser(UserRole.ADMIN);
        User target = seedPasswordUser(UserRole.CANDIDATE);
        User other = seedUser(UserRole.CANDIDATE);

        // 1. A CANDIDATE logs in; the token carries the persisted role.
        String beforeChange = loginAndGetToken(target);
        assertThat(roleClaim(beforeChange)).isEqualTo("CANDIDATE");

        // 2. An administrator promotes the account. The change is persisted at once.
        mockMvc.perform(put("/api/users/{id}/role", target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}")
                        .header("Authorization", bearerFor(promoter)))
                .andExpect(status().isOk());
        assertThat(userRepository.findById(target.getId()).orElseThrow().getRole())
                .isEqualTo(UserRole.ADMIN);

        // 3. The already-issued token keeps exactly the role it was signed with: it gains
        //    no new privilege and loses none. It can still act as its own owner...
        assertThat(roleClaim(beforeChange)).isEqualTo("CANDIDATE");
        mockMvc.perform(get("/api/users/{id}", target.getId())
                        .header("Authorization", "Bearer " + beforeChange))
                .andExpect(status().isOk());
        // ...but it cannot use the newly granted ADMIN authority until it expires.
        mockMvc.perform(put("/api/users/{id}/role", other.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"RECRUITER\"}")
                        .header("Authorization", "Bearer " + beforeChange))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(userRepository.findById(other.getId()).orElseThrow().getRole())
                .isEqualTo(UserRole.CANDIDATE);

        // 4. The next login reads the role from the database: the new token has it now —
        //    no session to refresh, no stale identity beyond the token lifetime.
        String afterChange = loginAndGetToken(target);
        assertThat(roleClaim(afterChange)).isEqualTo("ADMIN");
        mockMvc.perform(put("/api/users/{id}/role", other.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"RECRUITER\"}")
                        .header("Authorization", "Bearer " + afterChange))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("RECRUITER"));
    }

    @Test
    void disabledAccountCannotLogInAndItsIssuedTokenStaysValidOnlyUntilExpiry() throws Exception {
        User user = seedPasswordUser(UserRole.CANDIDATE);

        // A token obtained while the account was enabled.
        String issuedToken = loginAndGetToken(user);
        assertThat(roleClaim(issuedToken)).isEqualTo("CANDIDATE");

        // Disabling the account persists immediately.
        User disabled = userRepository.findById(user.getId()).orElseThrow();
        disabled.setEnabled(false);
        userRepository.saveAndFlush(disabled);

        // No new token may be minted, and the refusal reveals nothing about the account:
        // the same generic message an unknown email or a wrong password would produce.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(user.getEmail(), RAW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        // The outstanding token has no revocation store to answer to — it stays valid
        // only until its own expiry, which is the documented invalidation window.
        mockMvc.perform(get("/api/users/{id}", user.getId())
                        .header("Authorization", "Bearer " + issuedToken))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String bearerFor(User user) {
        return "Bearer " + jwtService.generateAccessToken(user.getId(), user.getRole()).token();
    }

    private String roleClaim(String token) throws Exception {
        return SignedJWT.parse(token).getJWTClaimsSet().getStringClaim("role");
    }

    private String loginAndGetToken(User user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(user.getEmail(), RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8),
                "$.accessToken");
    }

    private User seedUser(UserRole role) {
        return seedUser("rbac-" + UUID.randomUUID() + "@example.com", role, null);
    }

    private User seedPasswordUser(UserRole role) {
        return seedUser("rbac-login-" + UUID.randomUUID() + "@example.com", role,
                passwordEncoder.encode(RAW_PASSWORD));
    }

    private User seedUser(String email, UserRole role, String passwordHash) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Jane");
        user.setLastName("Doe");
        user.setRole(role);
        user.setPasswordHash(passwordHash);
        return userRepository.saveAndFlush(user);
    }

    private String createBodyWithRole(String email, String role) {
        return """
                {"email":"%s","firstName":"Escalation","lastName":"Attempt","password":"Sup3r-Secret!","role":"%s"}
                """.formatted(email, role);
    }

    private String loginBody(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private void assertCreatedRoleIsPersistedAsCandidate(MvcResult result, String marker)
            throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        UUID createdId = UUID.fromString(JsonPath.read(body, "$.id"));
        User persisted = userRepository.findById(createdId).orElseThrow();
        assertThat(persisted.getEmail()).startsWith(marker);
        assertThat(persisted.getRole()).isEqualTo(UserRole.CANDIDATE);
    }
}
