package com.hireflow.candidate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.hireflow.candidate.entity.Candidate;
import com.hireflow.candidate.repository.CandidateRepository;
import com.hireflow.candidate.security.TestSigningKeys;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Step 15 — candidate profile authorization tests.
 *
 * <p>Verifies the permission matrix through the real filter chain (real JWT parsing,
 * real {@code @PreAuthorize} evaluation, real database ownership checks):
 *
 * <ul>
 *   <li>CANDIDATE may create, read, and update only their own profile.</li>
 *   <li>CANDIDATE cannot update another candidate's profile.</li>
 *   <li>RECRUITER cannot create or update a candidate profile.</li>
 *   <li>ADMIN may create and update any profile.</li>
 *   <li>Only ADMIN may delete a profile.</li>
 *   <li>No token → 401; wrong role or wrong owner → 403.</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class CandidateAuthorizationIntegrationTest {

    private static final String DATABASE_NAME = "hireflow_candidate_rbac";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName(DATABASE_NAME);

    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("hireflow.jwt.signing-key", () -> TestSigningKeys.VALID);
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private CandidateRepository candidateRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    // ------------------------------------------------------------------
    // CANDIDATE — create own profile
    // ------------------------------------------------------------------

    @Test
    void candidateCanCreateOwnProfile() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidateBody("Java Developer"))
                        .header("Authorization", bearerFor(userId, "CANDIDATE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.headline").value("Java Developer"));
    }

    @Test
    void candidateCreateIgnoresUserIdInBody() throws Exception {
        UUID principalId = UUID.randomUUID();
        UUID fakeUserId = UUID.randomUUID();

        // Even if the body contains a different userId, the principal's id is used.
        mockMvc.perform(post("/api/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + fakeUserId + "\",\"headline\":\"Sneaky\"}")
                        .header("Authorization", bearerFor(principalId, "CANDIDATE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(principalId.toString()));

        // The fake user id is not the owner.
        assertThat(candidateRepository.existsByUserId(fakeUserId)).isFalse();
        assertThat(candidateRepository.existsByUserId(principalId)).isTrue();
    }

    // ------------------------------------------------------------------
    // RECRUITER cannot create or update candidate profiles
    // ------------------------------------------------------------------

    @Test
    void recruiterCannotCreateCandidateProfile() throws Exception {
        mockMvc.perform(post("/api/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidateBody("Recruiter trying"))
                        .header("Authorization", bearerFor(UUID.randomUUID(), "RECRUITER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void recruiterCannotUpdateCandidateProfile() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID candidateId = seedCandidate(ownerId);

        mockMvc.perform(put("/api/candidates/{id}", candidateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"Hijacked\"}")
                        .header("Authorization", bearerFor(UUID.randomUUID(), "RECRUITER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // The profile must be unchanged.
        assertThat(candidateRepository.findById(candidateId).orElseThrow().getHeadline())
                .isEqualTo("Senior Developer");
    }

    // ------------------------------------------------------------------
    // CANDIDATE ownership enforcement — cannot modify another's profile
    // ------------------------------------------------------------------

    @Test
    void candidateCannotUpdateAnotherCandidatesProfile() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        UUID candidateId = seedCandidate(ownerId);

        mockMvc.perform(put("/api/candidates/{id}", candidateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"Mine Now\"}")
                        .header("Authorization", bearerFor(strangerId, "CANDIDATE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Unchanged in the database.
        assertThat(candidateRepository.findById(candidateId).orElseThrow().getHeadline())
                .isEqualTo("Senior Developer");
    }

    @Test
    void candidateCanUpdateOwnProfile() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID candidateId = seedCandidate(ownerId);

        mockMvc.perform(put("/api/candidates/{id}", candidateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"Updated Headline\"}")
                        .header("Authorization", bearerFor(ownerId, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Updated Headline"));
    }

    // ------------------------------------------------------------------
    // ADMIN can update any candidate profile
    // ------------------------------------------------------------------

    @Test
    void adminCanUpdateAnyProfile() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID candidateId = seedCandidate(ownerId);

        mockMvc.perform(put("/api/candidates/{id}", candidateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"Admin Override\"}")
                        .header("Authorization", bearerFor(adminId, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Admin Override"));
    }

    // ------------------------------------------------------------------
    // Delete — ADMIN only
    // ------------------------------------------------------------------

    @Test
    void candidateCannotDeleteAProfile() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID candidateId = seedCandidate(ownerId);

        mockMvc.perform(delete("/api/candidates/{id}", candidateId)
                        .header("Authorization", bearerFor(ownerId, "CANDIDATE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(candidateRepository.findById(candidateId)).isPresent();
    }

    @Test
    void adminCanDeleteAProfile() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID candidateId = seedCandidate(ownerId);

        mockMvc.perform(delete("/api/candidates/{id}", candidateId)
                        .header("Authorization", bearerFor(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isNoContent());

        assertThat(candidateRepository.findById(candidateId)).isEmpty();
    }

    // ------------------------------------------------------------------
    // Read by user id — ownership
    // ------------------------------------------------------------------

    @Test
    void candidateCanReadOwnProfileByUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        seedCandidate(userId);

        mockMvc.perform(get("/api/candidates/user/{userId}", userId)
                        .header("Authorization", bearerFor(userId, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    void candidateCannotReadAnotherUsersProfileByUserId() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        seedCandidate(ownerId);

        mockMvc.perform(get("/api/candidates/user/{userId}", ownerId)
                        .header("Authorization", bearerFor(strangerId, "CANDIDATE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void recruiterCanReadAnyProfileByUserId() throws Exception {
        UUID ownerId = UUID.randomUUID();
        seedCandidate(ownerId);

        mockMvc.perform(get("/api/candidates/user/{userId}", ownerId)
                        .header("Authorization", bearerFor(UUID.randomUUID(), "RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(ownerId.toString()));
    }

    // ------------------------------------------------------------------
    // 401 vs 403 — never crossed
    // ------------------------------------------------------------------

    @Test
    void missingTokenProduces401NotForbidden() throws Exception {
        mockMvc.perform(post("/api/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidateBody("No Auth")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void recruiterGets403NotUnauthorized() throws Exception {
        mockMvc.perform(post("/api/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidateBody("Recruiter"))
                        .header("Authorization", bearerFor(UUID.randomUUID(), "RECRUITER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String bearerFor(UUID userId, String role) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issuer("hireflow-auth")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .claim("role", role)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(TestSigningKeys.VALID.getBytes(StandardCharsets.UTF_8)));
        return "Bearer " + jwt.serialize();
    }

    private UUID seedCandidate(UUID userId) {
        Candidate candidate = new Candidate();
        candidate.setUserId(userId);
        candidate.setHeadline("Senior Developer");
        return candidateRepository.saveAndFlush(candidate).getId();
    }

    private String candidateBody(String headline) {
        return "{\"headline\":\"" + headline + "\"}";
    }
}
