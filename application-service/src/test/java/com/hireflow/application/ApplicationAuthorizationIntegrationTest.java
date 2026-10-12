package com.hireflow.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.hireflow.application.entity.Application;
import com.hireflow.application.entity.ApplicationStatus;
import com.hireflow.application.repository.ApplicationRepository;
import com.hireflow.application.security.CrossServiceOwnershipClient;
import com.hireflow.application.security.TestSigningKeys;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Step 15 — application service authorization and cross-service ownership tests.
 *
 * <p>Verifies the permission matrix through the real filter chain:
 *
 * <ul>
 *   <li>CANDIDATE may apply using own candidate identity only.</li>
 *   <li>CANDIDATE cannot view or withdraw another candidate's applications.</li>
 *   <li>RECRUITER cannot apply for jobs or view candidate application lists.</li>
 *   <li>RECRUITER may view and update status only for applications to jobs they own.</li>
 *   <li>ADMIN may manage all applications.</li>
 *   <li>Fails closed when cross-service ownership cannot be confirmed.</li>
 *   <li>No token → 401; wrong role or wrong owner → 403.</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class ApplicationAuthorizationIntegrationTest {

    private static final String DATABASE_NAME = "hireflow_app_rbac";

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

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public StubCrossServiceOwnershipClient stubOwnershipClient() {
            return new StubCrossServiceOwnershipClient();
        }
    }

    public static class StubCrossServiceOwnershipClient implements CrossServiceOwnershipClient {
        private final Set<String> candidateOwners = new HashSet<>();
        private final Set<String> jobOwners = new HashSet<>();
        private boolean failClosed = false;

        public void allowCandidate(UUID candidateId, UUID userId) {
            candidateOwners.add(candidateId + ":" + userId);
        }

        public void allowJob(UUID jobId, UUID recruiterId) {
            jobOwners.add(jobId + ":" + recruiterId);
        }

        public void setFailClosed(boolean failClosed) {
            this.failClosed = failClosed;
        }

        public void clear() {
            candidateOwners.clear();
            jobOwners.clear();
            failClosed = false;
        }

        @Override
        public boolean isCandidateOwner(UUID candidateId, UUID userId) {
            if (failClosed) return false;
            return candidateOwners.contains(candidateId + ":" + userId);
        }

        @Override
        public boolean isJobOwner(UUID jobId, UUID recruiterId) {
            if (failClosed) return false;
            return jobOwners.contains(jobId + ":" + recruiterId);
        }
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private StubCrossServiceOwnershipClient stubOwnershipClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        stubOwnershipClient.clear();
        mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    // ------------------------------------------------------------------
    // Apply for a job — CANDIDATE only with own candidate identity
    // ------------------------------------------------------------------

    @Test
    void candidateCanApplyWithOwnCandidateIdentity() throws Exception {
        UUID candidateUserId = UUID.randomUUID();
        UUID candidateProfileId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        stubOwnershipClient.allowCandidate(candidateProfileId, candidateUserId);

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(candidateProfileId, jobId))
                        .header("Authorization", bearerFor(candidateUserId, "CANDIDATE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.candidateId").value(candidateProfileId.toString()))
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("APPLIED"));
    }

    @Test
    void candidateCannotApplyWithAnotherCandidatesIdentity() throws Exception {
        UUID candidateUserId = UUID.randomUUID();
        UUID strangerProfileId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        // Not registered in stubOwnershipClient -> fails ownership
        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(strangerProfileId, jobId))
                        .header("Authorization", bearerFor(candidateUserId, "CANDIDATE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void recruiterCannotApplyForJob() throws Exception {
        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(UUID.randomUUID(), UUID.randomUUID()))
                        .header("Authorization", bearerFor(UUID.randomUUID(), "RECRUITER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ------------------------------------------------------------------
    // View applications by candidateId — CANDIDATE own profile only
    // ------------------------------------------------------------------

    @Test
    void candidateCanViewOwnApplications() throws Exception {
        UUID candidateUserId = UUID.randomUUID();
        UUID candidateProfileId = UUID.randomUUID();
        seedApplication(candidateProfileId, UUID.randomUUID());

        stubOwnershipClient.allowCandidate(candidateProfileId, candidateUserId);

        mockMvc.perform(get("/api/applications/candidate/{candidateId}", candidateProfileId)
                        .header("Authorization", bearerFor(candidateUserId, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].candidateId").value(candidateProfileId.toString()));
    }

    @Test
    void candidateCannotViewAnotherCandidatesApplications() throws Exception {
        UUID strangerProfileId = UUID.randomUUID();
        seedApplication(strangerProfileId, UUID.randomUUID());

        mockMvc.perform(get("/api/applications/candidate/{candidateId}", strangerProfileId)
                        .header("Authorization", bearerFor(UUID.randomUUID(), "CANDIDATE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void recruiterCannotViewCandidateApplications() throws Exception {
        mockMvc.perform(get("/api/applications/candidate/{candidateId}", UUID.randomUUID())
                        .header("Authorization", bearerFor(UUID.randomUUID(), "RECRUITER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ------------------------------------------------------------------
    // View applications by jobId — RECRUITER own jobs only
    // ------------------------------------------------------------------

    @Test
    void recruiterCanViewApplicationsForOwnJob() throws Exception {
        UUID recruiterUserId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        seedApplication(UUID.randomUUID(), jobId);

        stubOwnershipClient.allowJob(jobId, recruiterUserId);

        mockMvc.perform(get("/api/applications/job/{jobId}", jobId)
                        .header("Authorization", bearerFor(recruiterUserId, "RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobId").value(jobId.toString()));
    }

    @Test
    void recruiterCannotViewApplicationsForAnotherRecruitersJob() throws Exception {
        UUID jobId = UUID.randomUUID();
        seedApplication(UUID.randomUUID(), jobId);

        mockMvc.perform(get("/api/applications/job/{jobId}", jobId)
                        .header("Authorization", bearerFor(UUID.randomUUID(), "RECRUITER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void candidateCannotViewApplicationsByJobId() throws Exception {
        mockMvc.perform(get("/api/applications/job/{jobId}", UUID.randomUUID())
                        .header("Authorization", bearerFor(UUID.randomUUID(), "CANDIDATE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ------------------------------------------------------------------
    // Update application status — RECRUITER own jobs only
    // ------------------------------------------------------------------

    @Test
    void recruiterCanUpdateStatusForOwnJob() throws Exception {
        UUID recruiterUserId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Application application = seedApplication(UUID.randomUUID(), jobId);

        stubOwnershipClient.allowJob(jobId, recruiterUserId);

        mockMvc.perform(patch("/api/applications/{id}/status", application.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHORTLISTED\"}")
                        .header("Authorization", bearerFor(recruiterUserId, "RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHORTLISTED"));
    }

    @Test
    void recruiterCannotUpdateStatusForAnotherRecruitersJob() throws Exception {
        UUID jobId = UUID.randomUUID();
        Application application = seedApplication(UUID.randomUUID(), jobId);

        mockMvc.perform(patch("/api/applications/{id}/status", application.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHORTLISTED\"}")
                        .header("Authorization", bearerFor(UUID.randomUUID(), "RECRUITER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void candidateCannotUpdateApplicationStatus() throws Exception {
        Application application = seedApplication(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(patch("/api/applications/{id}/status", application.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHORTLISTED\"}")
                        .header("Authorization", bearerFor(UUID.randomUUID(), "CANDIDATE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ------------------------------------------------------------------
    // Delete application — CANDIDATE own application withdrawal
    // ------------------------------------------------------------------

    @Test
    void candidateCanWithdrawOwnApplication() throws Exception {
        UUID candidateUserId = UUID.randomUUID();
        UUID candidateProfileId = UUID.randomUUID();
        Application application = seedApplication(candidateProfileId, UUID.randomUUID());

        stubOwnershipClient.allowCandidate(candidateProfileId, candidateUserId);

        mockMvc.perform(delete("/api/applications/{id}", application.getId())
                        .header("Authorization", bearerFor(candidateUserId, "CANDIDATE")))
                .andExpect(status().isNoContent());

        assertThat(applicationRepository.findById(application.getId())).isEmpty();
    }

    @Test
    void candidateCannotWithdrawAnotherCandidatesApplication() throws Exception {
        Application application = seedApplication(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(delete("/api/applications/{id}", application.getId())
                        .header("Authorization", bearerFor(UUID.randomUUID(), "CANDIDATE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void recruiterCannotWithdrawApplications() throws Exception {
        Application application = seedApplication(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(delete("/api/applications/{id}", application.getId())
                        .header("Authorization", bearerFor(UUID.randomUUID(), "RECRUITER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ------------------------------------------------------------------
    // ADMIN can manage all applications
    // ------------------------------------------------------------------

    @Test
    void adminCanManageAllApplications() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Application application = seedApplication(candidateId, jobId);

        // Admin reads
        mockMvc.perform(get("/api/applications/{id}", application.getId())
                        .header("Authorization", bearerFor(adminId, "ADMIN")))
                .andExpect(status().isOk());

        // Admin updates status
        mockMvc.perform(patch("/api/applications/{id}/status", application.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"HIRED\"}")
                        .header("Authorization", bearerFor(adminId, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HIRED"));

        // Admin deletes
        mockMvc.perform(delete("/api/applications/{id}", application.getId())
                        .header("Authorization", bearerFor(adminId, "ADMIN")))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------
    // Fail-closed verification
    // ------------------------------------------------------------------

    @Test
    void failsClosedWhenOwnershipVerificationServiceUnavailable() throws Exception {
        UUID candidateUserId = UUID.randomUUID();
        UUID candidateProfileId = UUID.randomUUID();
        stubOwnershipClient.allowCandidate(candidateProfileId, candidateUserId);
        stubOwnershipClient.setFailClosed(true);

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(candidateProfileId, UUID.randomUUID()))
                        .header("Authorization", bearerFor(candidateUserId, "CANDIDATE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void missingTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
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

    private Application seedApplication(UUID candidateId, UUID jobId) {
        Application application = new Application();
        application.setCandidateId(candidateId);
        application.setJobId(jobId);
        application.setStatus(ApplicationStatus.APPLIED);
        return applicationRepository.saveAndFlush(application);
    }

    private String createBody(UUID candidateId, UUID jobId) {
        return """
                {"candidateId":"%s","jobId":"%s","coverLetter":"Interested in position"}
                """.formatted(candidateId, jobId);
    }
}
