package com.hireflow.application;

import com.hireflow.application.security.TestSigningKeys;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.hireflow.application.entity.Application;
import com.hireflow.application.entity.ApplicationStatus;
import com.hireflow.application.repository.ApplicationRepository;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class ApplicationApiIntegrationTest {

    private static final String DATABASE_NAME = "hireflow_application";

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
    private ApplicationRepository applicationRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void createsApplication() throws Exception {
        UUID candidateId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        mockMvc.perform(authenticated(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(candidateId, jobId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.candidateId").value(candidateId.toString()))
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.coverLetter").value("I would love to work on this role"))
                .andExpect(jsonPath("$.appliedAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void returnsLocationHeaderOnCreate() throws Exception {
        UUID candidateId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        MvcResult result = mockMvc.perform(authenticated(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(candidateId, jobId))))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String location = result.getResponse().getHeader("Location");
        assertThat(location).endsWith("/api/applications/" + JsonPath.read(body, "$.id"));
    }

    @Test
    void rejectsNonJsonCreatePayload() throws Exception {
        mockMvc.perform(authenticated(post("/api/applications")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json")))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void ignoresClientSuppliedStatusOnCreate() throws Exception {
        UUID candidateId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        String payload = """
                {"candidateId":"%s","jobId":"%s","status":"HIRED"}
                """.formatted(candidateId, jobId);

        mockMvc.perform(authenticated(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"));
    }

    @Test
    void rejectsInvalidCreatePayloads() throws Exception {
        List<String> invalidPayloads = List.of(
                "{\"jobId\":\"" + UUID.randomUUID() + "\"}",
                "{\"candidateId\":\"" + UUID.randomUUID() + "\"}",
                "{\"candidateId\":\"not-a-uuid\",\"jobId\":\"" + UUID.randomUUID() + "\"}",
                "{\"candidateId\":\"" + UUID.randomUUID() + "\",\"jobId\":\"not-a-uuid\"}",
                "{\"candidateId\":\"" + UUID.randomUUID() + "\",\"jobId\":\"" + UUID.randomUUID()
                        + "\",\"coverLetter\":\"" + "x".repeat(5001) + "\"}",
                "{}");

        for (String payload : invalidPayloads) {
            mockMvc.perform(authenticated(post("/api/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void rejectsDuplicateApplication() throws Exception {
        UUID candidateId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        seedApplication(candidateId, jobId);

        mockMvc.perform(authenticated(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(candidateId, jobId))))
                .andExpect(status().isConflict());
    }

    @Test
    void getApplicationById() throws Exception {
        Application application = seedApplication(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(authenticated(get("/api/applications/{id}", application.getId())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(application.getId().toString()))
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.coverLetter").value("I would love to work on this role"));
    }

    @Test
    void returnsNotFoundForUnknownApplicationId() throws Exception {
        mockMvc.perform(authenticated(get("/api/applications/{id}", UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsBadRequestForMalformedApplicationId() throws Exception {
        mockMvc.perform(authenticated(get("/api/applications/{id}", "not-a-uuid")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsBadRequestForMalformedIdOnStatusUpdate() throws Exception {
        mockMvc.perform(authenticated(patch("/api/applications/{id}/status", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"UNDER_REVIEW\"}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCandidateApplications() throws Exception {
        UUID candidateId = UUID.randomUUID();
        Application first = seedApplication(candidateId, UUID.randomUUID());
        Application second = seedApplication(candidateId, UUID.randomUUID());
        seedApplication(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(authenticated(get("/api/applications/candidate/{candidateId}", candidateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id").value(containsInAnyOrder(
                        first.getId().toString(), second.getId().toString())));
    }

    @Test
    void returnsEmptyListForCandidateWithoutApplications() throws Exception {
        mockMvc.perform(authenticated(get("/api/applications/candidate/{candidateId}", UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void returnsBadRequestForMalformedCandidateId() throws Exception {
        mockMvc.perform(authenticated(get("/api/applications/candidate/{candidateId}", "not-a-uuid")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getJobApplications() throws Exception {
        UUID jobId = UUID.randomUUID();
        Application first = seedApplication(UUID.randomUUID(), jobId);
        Application second = seedApplication(UUID.randomUUID(), jobId);
        seedApplication(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(authenticated(get("/api/applications/job/{jobId}", jobId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id").value(containsInAnyOrder(
                        first.getId().toString(), second.getId().toString())));
    }

    @Test
    void returnsEmptyListForJobWithoutApplications() throws Exception {
        mockMvc.perform(authenticated(get("/api/applications/job/{jobId}", UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void returnsBadRequestForMalformedJobId() throws Exception {
        mockMvc.perform(authenticated(get("/api/applications/job/{jobId}", "not-a-uuid")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesApplicationStatus() throws Exception {
        Application application = seedApplication(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(authenticated(patch("/api/applications/{id}/status", application.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"UNDER_REVIEW\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(application.getId().toString()))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        Application reloaded = applicationRepository.findById(application.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
    }

    @Test
    void rejectsMissingStatusOnUpdate() throws Exception {
        Application application = seedApplication(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(authenticated(patch("/api/applications/{id}/status", application.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownStatusOnUpdate() throws Exception {
        Application application = seedApplication(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(authenticated(patch("/api/applications/{id}/status", application.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NOT_A_STATUS\"}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundWhenUpdatingUnknownApplication() throws Exception {
        mockMvc.perform(authenticated(patch("/api/applications/{id}/status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}")))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletesApplication() throws Exception {
        Application application = seedApplication(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(authenticated(delete("/api/applications/{id}", application.getId())))
                .andExpect(status().isNoContent());

        assertThat(applicationRepository.findById(application.getId())).isEmpty();

        mockMvc.perform(authenticated(get("/api/applications/{id}", application.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundWhenDeletingUnknownApplication() throws Exception {
        mockMvc.perform(authenticated(delete("/api/applications/{id}", UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    private Application seedApplication(UUID candidateId, UUID jobId) {
        Application application = new Application();
        application.setCandidateId(candidateId);
        application.setJobId(jobId);
        application.setStatus(ApplicationStatus.APPLIED);
        application.setCoverLetter("I would love to work on this role");
        return applicationRepository.saveAndFlush(application);
    }

    private String createBody(UUID candidateId, UUID jobId) {
        return """
                {"candidateId":"%s","jobId":"%s","coverLetter":"I would love to work on this role"}
                """.formatted(candidateId, jobId);
    }

    /** Every business endpoint requires a valid bearer token; tests authenticate explicitly. */
    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + signedBearerToken());
    }

    /**
     * A valid access token signed exactly as auth-service signs it, so the request travels
     * through the real {@code JwtAuthenticationFilter} instead of being injected straight
     * into the security context by a test post-processor.
     */
    private static String signedBearerToken() {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(UUID.randomUUID().toString())
                    .issuer("hireflow-auth")
                    .issueTime(new Date())
                    .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                    .claim("role", "CANDIDATE")
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(TestSigningKeys.VALID.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Failed to sign test bearer token", ex);
        }
    }
}
