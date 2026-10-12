package com.hireflow.job;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
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

import com.hireflow.job.entity.EmploymentType;
import com.hireflow.job.entity.Job;
import com.hireflow.job.entity.JobStatus;
import com.hireflow.job.repository.JobRepository;
import com.hireflow.job.security.TestSigningKeys;
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
 * Step 15 — job requisition authorization and ownership tests.
 *
 * <p>Verifies the permission matrix through the real filter chain:
 *
 * <ul>
 *   <li>RECRUITER and ADMIN may create jobs.</li>
 *   <li>CANDIDATE cannot create, modify, or delete jobs.</li>
 *   <li>Recruiter impersonation is prevented (recruiterId is derived from token).</li>
 *   <li>RECRUITER may modify and delete only jobs they own.</li>
 *   <li>ADMIN may modify and delete any job.</li>
 *   <li>Browsing jobs is open to all authenticated callers.</li>
 *   <li>No token → 401; wrong role or wrong owner → 403.</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class JobAuthorizationIntegrationTest {

    private static final String DATABASE_NAME = "hireflow_job_rbac";

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
    private JobRepository jobRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    // ------------------------------------------------------------------
    // RECRUITER — create job & impersonation prevention
    // ------------------------------------------------------------------

    @Test
    void recruiterCanCreateJob() throws Exception {
        UUID recruiterId = UUID.randomUUID();

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(recruiterId, "Senior Java Developer"))
                        .header("Authorization", bearerFor(recruiterId, "RECRUITER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recruiterId").value(recruiterId.toString()))
                .andExpect(jsonPath("$.title").value("Senior Java Developer"));
    }

    @Test
    void recruiterCreatePreventsImpersonation() throws Exception {
        UUID tokenRecruiterId = UUID.randomUUID();
        UUID fakeRecruiterId = UUID.randomUUID();

        // The body has fakeRecruiterId, but the token has tokenRecruiterId.
        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(fakeRecruiterId, "Recruiter Test"))
                        .header("Authorization", bearerFor(tokenRecruiterId, "RECRUITER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recruiterId").value(tokenRecruiterId.toString()));

        // Database verified: the owner is tokenRecruiterId, not fakeRecruiterId.
        assertThat(jobRepository.findByRecruiterId(fakeRecruiterId)).isEmpty();
        assertThat(jobRepository.findByRecruiterId(tokenRecruiterId)).isNotEmpty();
    }

    // ------------------------------------------------------------------
    // CANDIDATE cannot create, update, or delete jobs
    // ------------------------------------------------------------------

    @Test
    void candidateCannotCreateJob() throws Exception {
        UUID candidateId = UUID.randomUUID();

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(candidateId, "Unauthorized Job"))
                        .header("Authorization", bearerFor(candidateId, "CANDIDATE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void candidateCannotUpdateJob() throws Exception {
        UUID ownerRecruiterId = UUID.randomUUID();
        UUID jobId = seedJob(ownerRecruiterId, "Original Title");

        mockMvc.perform(put("/api/jobs/{id}", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hacked Title\"}")
                        .header("Authorization", bearerFor(UUID.randomUUID(), "CANDIDATE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(jobRepository.findById(jobId).orElseThrow().getTitle()).isEqualTo("Original Title");
    }

    @Test
    void candidateCannotDeleteJob() throws Exception {
        UUID ownerRecruiterId = UUID.randomUUID();
        UUID jobId = seedJob(ownerRecruiterId, "Original Title");

        mockMvc.perform(delete("/api/jobs/{id}", jobId)
                        .header("Authorization", bearerFor(UUID.randomUUID(), "CANDIDATE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(jobRepository.findById(jobId)).isPresent();
    }

    // ------------------------------------------------------------------
    // Recruiter ownership enforcement on update & delete
    // ------------------------------------------------------------------

    @Test
    void recruiterCannotUpdateAnotherRecruitersJob() throws Exception {
        UUID ownerRecruiterId = UUID.randomUUID();
        UUID strangerRecruiterId = UUID.randomUUID();
        UUID jobId = seedJob(ownerRecruiterId, "Original Title");

        mockMvc.perform(put("/api/jobs/{id}", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hijacked Title\"}")
                        .header("Authorization", bearerFor(strangerRecruiterId, "RECRUITER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(jobRepository.findById(jobId).orElseThrow().getTitle()).isEqualTo("Original Title");
    }

    @Test
    void recruiterCanUpdateOwnJob() throws Exception {
        UUID ownerRecruiterId = UUID.randomUUID();
        UUID jobId = seedJob(ownerRecruiterId, "Original Title");

        mockMvc.perform(put("/api/jobs/{id}", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated Title\"}")
                        .header("Authorization", bearerFor(ownerRecruiterId, "RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"));

        assertThat(jobRepository.findById(jobId).orElseThrow().getTitle()).isEqualTo("Updated Title");
    }

    @Test
    void recruiterCannotDeleteAnotherRecruitersJob() throws Exception {
        UUID ownerRecruiterId = UUID.randomUUID();
        UUID strangerRecruiterId = UUID.randomUUID();
        UUID jobId = seedJob(ownerRecruiterId, "Original Title");

        mockMvc.perform(delete("/api/jobs/{id}", jobId)
                        .header("Authorization", bearerFor(strangerRecruiterId, "RECRUITER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(jobRepository.findById(jobId)).isPresent();
    }

    @Test
    void recruiterCanDeleteOwnJob() throws Exception {
        UUID ownerRecruiterId = UUID.randomUUID();
        UUID jobId = seedJob(ownerRecruiterId, "Original Title");

        mockMvc.perform(delete("/api/jobs/{id}", jobId)
                        .header("Authorization", bearerFor(ownerRecruiterId, "RECRUITER")))
                .andExpect(status().isNoContent());

        assertThat(jobRepository.findById(jobId)).isEmpty();
    }

    // ------------------------------------------------------------------
    // ADMIN can update and delete any job
    // ------------------------------------------------------------------

    @Test
    void adminCanUpdateAnyJob() throws Exception {
        UUID ownerRecruiterId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID jobId = seedJob(ownerRecruiterId, "Original Title");

        mockMvc.perform(put("/api/jobs/{id}", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Admin Override\"}")
                        .header("Authorization", bearerFor(adminId, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Admin Override"));
    }

    @Test
    void adminCanDeleteAnyJob() throws Exception {
        UUID ownerRecruiterId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID jobId = seedJob(ownerRecruiterId, "Original Title");

        mockMvc.perform(delete("/api/jobs/{id}", jobId)
                        .header("Authorization", bearerFor(adminId, "ADMIN")))
                .andExpect(status().isNoContent());

        assertThat(jobRepository.findById(jobId)).isEmpty();
    }

    // ------------------------------------------------------------------
    // Browsing jobs — accessible to CANDIDATE
    // ------------------------------------------------------------------

    @Test
    void candidateCanBrowseJobById() throws Exception {
        UUID recruiterId = UUID.randomUUID();
        UUID jobId = seedJob(recruiterId, "Public Job");

        mockMvc.perform(get("/api/jobs/{id}", jobId)
                        .header("Authorization", bearerFor(UUID.randomUUID(), "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Public Job"));
    }

    @Test
    void candidateCanBrowseJobsByRecruiterId() throws Exception {
        UUID recruiterId = UUID.randomUUID();
        seedJob(recruiterId, "Job 1");

        mockMvc.perform(get("/api/jobs/recruiter/{recruiterId}", recruiterId)
                        .header("Authorization", bearerFor(UUID.randomUUID(), "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Job 1"));
    }

    // ------------------------------------------------------------------
    // 401 vs 403 contracts
    // ------------------------------------------------------------------

    @Test
    void missingTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(UUID.randomUUID(), "No Token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void candidateReceives403Not401OnJobCreation() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(UUID.randomUUID(), "Forbidden"))
                        .header("Authorization", bearerFor(UUID.randomUUID(), "CANDIDATE")))
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

    private UUID seedJob(UUID recruiterId, String title) {
        Job job = new Job();
        job.setRecruiterId(recruiterId);
        job.setTitle(title);
        job.setDescription("Description for " + title);
        job.setLocation("Berlin");
        job.setEmploymentType(EmploymentType.FULL_TIME);
        job.setStatus(JobStatus.OPEN);
        return jobRepository.saveAndFlush(job).getId();
    }

    private String createBody(UUID recruiterId, String title) {
        return """
                {"recruiterId":"%s","title":"%s",
                "description":"Build and maintain Spring Boot services","location":"Berlin",
                "employmentType":"FULL_TIME","experienceMin":3,"experienceMax":6,
                "salaryMin":60000,"salaryMax":90000,"skills":["Java","Spring"]}
                """.formatted(recruiterId, title);
    }
}
