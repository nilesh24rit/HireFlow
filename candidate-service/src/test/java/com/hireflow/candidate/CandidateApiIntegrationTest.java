package com.hireflow.candidate;

import com.hireflow.candidate.security.TestSigningKeys;

import java.nio.charset.StandardCharsets;
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

import com.hireflow.candidate.entity.Candidate;
import com.hireflow.candidate.entity.CandidateSkill;
import com.hireflow.candidate.repository.CandidateRepository;
import com.hireflow.candidate.repository.CandidateSkillRepository;
import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class CandidateApiIntegrationTest {

    private static final String DATABASE_NAME = "hireflow_candidate";

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
    private CandidateRepository candidateRepository;

    @Autowired
    private CandidateSkillRepository candidateSkillRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void createsCandidate() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(authenticated(post("/api/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(userId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.headline").value("Senior Java Developer"))
                .andExpect(jsonPath("$.location").value("Berlin"))
                .andExpect(jsonPath("$.yearsOfExperience").value(5))
                .andExpect(jsonPath("$.skills[0]").value("Java"))
                .andExpect(jsonPath("$.skills[1]").value("Spring"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void returnsLocationHeaderOnCreate() throws Exception {
        UUID userId = UUID.randomUUID();

        MvcResult result = mockMvc.perform(authenticated(post("/api/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(userId))))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String location = result.getResponse().getHeader("Location");
        assertThat(location).endsWith("/api/candidates/" + JsonPath.read(body, "$.id"));
    }

    @Test
    void rejectsNonJsonCreatePayload() throws Exception {
        mockMvc.perform(authenticated(post("/api/candidates")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json")))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void returnsBadRequestForMalformedCandidateId() throws Exception {
        mockMvc.perform(authenticated(get("/api/candidates/{id}", "not-a-uuid")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsBadRequestForMalformedUserIdLookup() throws Exception {
        mockMvc.perform(authenticated(get("/api/candidates/user/{userId}", "not-a-uuid")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateCandidateForSameUser() throws Exception {
        UUID userId = UUID.randomUUID();
        seedCandidate(userId);

        mockMvc.perform(authenticated(post("/api/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(userId))))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidCreatePayloads() throws Exception {
        List<String> invalidPayloads = List.of(
                "{\"headline\":\"Missing user id\"}",
                "{\"userId\":\"" + UUID.randomUUID() + "\",\"yearsOfExperience\":-1}",
                "{\"userId\":\"" + UUID.randomUUID() + "\",\"resumeUrl\":\"not-a-url\"}",
                "{\"userId\":\"" + UUID.randomUUID() + "\",\"skills\":[\"Java\",\" \"]}",
                "{\"userId\":\"" + UUID.randomUUID() + "\",\"headline\":\"" + "x".repeat(201) + "\"}");

        for (String payload : invalidPayloads) {
            mockMvc.perform(authenticated(post("/api/candidates")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void getCandidateById() throws Exception {
        Candidate candidate = seedCandidate(UUID.randomUUID());

        mockMvc.perform(authenticated(get("/api/candidates/{id}", candidate.getId())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(candidate.getId().toString()))
                .andExpect(jsonPath("$.headline").value("Senior Java Developer"))
                .andExpect(jsonPath("$.skills[0]").value("Java"))
                .andExpect(jsonPath("$.skills[1]").value("Spring"));
    }

    @Test
    void returnsNotFoundForUnknownCandidateId() throws Exception {
        mockMvc.perform(authenticated(get("/api/candidates/{id}", UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCandidateByUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        Candidate candidate = seedCandidate(userId);

        mockMvc.perform(authenticated(get("/api/candidates/user/{userId}", userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(candidate.getId().toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    void returnsNotFoundForUnknownUserId() throws Exception {
        mockMvc.perform(authenticated(get("/api/candidates/user/{userId}", UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatesCandidateAndReplacesSkills() throws Exception {
        Candidate candidate = seedCandidate(UUID.randomUUID());
        String payload = """
                {"headline":"Staff Engineer","location":"Munich","skills":["Kafka","Docker"]}
                """;

        mockMvc.perform(authenticated(put("/api/candidates/{id}", candidate.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Staff Engineer"))
                .andExpect(jsonPath("$.location").value("Munich"))
                .andExpect(jsonPath("$.summary").value("Experienced backend engineer"))
                .andExpect(jsonPath("$.skills[0]").value("Docker"))
                .andExpect(jsonPath("$.skills[1]").value("Kafka"));

        Candidate reloaded = candidateRepository.findById(candidate.getId()).orElseThrow();
        assertThat(reloaded.getHeadline()).isEqualTo("Staff Engineer");
        assertThat(candidateSkillRepository.findByCandidateId(candidate.getId()))
                .extracting(CandidateSkill::getSkill)
                .containsExactly("Docker", "Kafka");
    }

    @Test
    void returnsNotFoundWhenUpdatingUnknownCandidate() throws Exception {
        mockMvc.perform(authenticated(put("/api/candidates/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"Staff Engineer\"}")))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidUpdatePayload() throws Exception {
        Candidate candidate = seedCandidate(UUID.randomUUID());

        mockMvc.perform(authenticated(put("/api/candidates/{id}", candidate.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yearsOfExperience\":-2,\"skills\":[\"Java\",\" \"]}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletesCandidate() throws Exception {
        Candidate candidate = seedCandidate(UUID.randomUUID());

        mockMvc.perform(authenticated(delete("/api/candidates/{id}", candidate.getId())))
                .andExpect(status().isNoContent());

        assertThat(candidateRepository.findById(candidate.getId())).isEmpty();
        assertThat(candidateSkillRepository.findByCandidateId(candidate.getId())).isEmpty();

        mockMvc.perform(authenticated(get("/api/candidates/{id}", candidate.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundWhenDeletingUnknownCandidate() throws Exception {
        mockMvc.perform(authenticated(delete("/api/candidates/{id}", UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    private Candidate seedCandidate(UUID userId) {
        Candidate candidate = new Candidate();
        candidate.setUserId(userId);
        candidate.setHeadline("Senior Java Developer");
        candidate.setSummary("Experienced backend engineer");
        candidate.setLocation("Berlin");
        candidate.setYearsOfExperience(5);
        candidate.setCurrentCompany("Acme");
        candidate.setCurrentJobTitle("Software Engineer");
        candidate.setResumeUrl("https://resume.example.com/jane.pdf");
        candidate.setLinkedinUrl("https://linkedin.com/in/jane");
        candidate.setGithubUrl("https://github.com/jane");
        Candidate saved = candidateRepository.saveAndFlush(candidate);
        saveSkill(saved.getId(), "Java");
        saveSkill(saved.getId(), "Spring");
        return saved;
    }

    private void saveSkill(UUID candidateId, String skill) {
        CandidateSkill candidateSkill = new CandidateSkill();
        candidateSkill.setCandidateId(candidateId);
        candidateSkill.setSkill(skill);
        candidateSkillRepository.saveAndFlush(candidateSkill);
    }

    private String createBody(UUID userId) {
        return """
                {"userId":"%s","headline":"Senior Java Developer","summary":"Experienced backend engineer",
                "location":"Berlin","yearsOfExperience":5,"currentCompany":"Acme",
                "currentJobTitle":"Software Engineer","resumeUrl":"https://resume.example.com/jane.pdf",
                "linkedinUrl":"https://linkedin.com/in/jane","githubUrl":"https://github.com/jane",
                "skills":["Java","Spring"]}
                """.formatted(userId);
    }

    /** Every business endpoint requires a valid bearer token; tests authenticate explicitly. */
    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request.with(jwt());
    }
}
