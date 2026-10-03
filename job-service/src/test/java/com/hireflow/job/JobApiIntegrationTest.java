package com.hireflow.job;

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
import com.hireflow.job.entity.JobSkill;
import com.hireflow.job.entity.JobStatus;
import com.hireflow.job.repository.JobRepository;
import com.hireflow.job.repository.JobSkillRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class JobApiIntegrationTest {

    private static final String DATABASE_NAME = "hireflow_job";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName(DATABASE_NAME);

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobSkillRepository jobSkillRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void createsJob() throws Exception {
        UUID recruiterId = UUID.randomUUID();

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(recruiterId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.recruiterId").value(recruiterId.toString()))
                .andExpect(jsonPath("$.title").value("Senior Java Developer"))
                .andExpect(jsonPath("$.location").value("Berlin"))
                .andExpect(jsonPath("$.employmentType").value("FULL_TIME"))
                .andExpect(jsonPath("$.experienceMin").value(3))
                .andExpect(jsonPath("$.experienceMax").value(6))
                .andExpect(jsonPath("$.salaryMin").value(60000))
                .andExpect(jsonPath("$.salaryMax").value(90000))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.skills[0]").value("Java"))
                .andExpect(jsonPath("$.skills[1]").value("Spring"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void rejectsInvalidCreatePayloads() throws Exception {
        List<String> invalidPayloads = List.of(
                "{\"title\":\"Missing recruiter\",\"description\":\"Description\",\"employmentType\":\"FULL_TIME\"}",
                "{\"recruiterId\":\"" + UUID.randomUUID() + "\",\"description\":\"Description\",\"employmentType\":\"FULL_TIME\"}",
                "{\"recruiterId\":\"" + UUID.randomUUID() + "\",\"title\":\"Missing description\",\"employmentType\":\"FULL_TIME\"}",
                "{\"recruiterId\":\"" + UUID.randomUUID() + "\",\"title\":\"Missing type\",\"description\":\"Description\"}",
                "{\"recruiterId\":\"" + UUID.randomUUID() + "\",\"title\":\"T\",\"description\":\"D\","
                        + "\"employmentType\":\"FULL_TIME\",\"experienceMin\":-1}",
                "{\"recruiterId\":\"" + UUID.randomUUID() + "\",\"title\":\"T\",\"description\":\"D\","
                        + "\"employmentType\":\"FULL_TIME\",\"experienceMin\":9,\"experienceMax\":2}",
                "{\"recruiterId\":\"" + UUID.randomUUID() + "\",\"title\":\"T\",\"description\":\"D\","
                        + "\"employmentType\":\"FULL_TIME\",\"salaryMin\":-5}",
                "{\"recruiterId\":\"" + UUID.randomUUID() + "\",\"title\":\"T\",\"description\":\"D\","
                        + "\"employmentType\":\"FULL_TIME\",\"salaryMin\":90000,\"salaryMax\":60000}",
                "{\"recruiterId\":\"" + UUID.randomUUID() + "\",\"title\":\"T\",\"description\":\"D\","
                        + "\"employmentType\":\"FULL_TIME\",\"skills\":[\"Java\",\" \"]}",
                "{\"recruiterId\":\"" + UUID.randomUUID() + "\",\"title\":\"T\",\"description\":\"D\","
                        + "\"employmentType\":\"WIZARD\"}",
                "{\"recruiterId\":\"" + UUID.randomUUID() + "\",\"title\":\"T\",\"description\":\"D\","
                        + "\"employmentType\":\"FULL_TIME\",\"status\":\"UNKNOWN\"}");

        for (String payload : invalidPayloads) {
            mockMvc.perform(post("/api/jobs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void getJobById() throws Exception {
        Job job = seedJob(UUID.randomUUID());

        mockMvc.perform(get("/api/jobs/{id}", job.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(job.getId().toString()))
                .andExpect(jsonPath("$.title").value("Senior Java Developer"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.skills[0]").value("Java"))
                .andExpect(jsonPath("$.skills[1]").value("Spring"));
    }

    @Test
    void returnsNotFoundForUnknownJobId() throws Exception {
        mockMvc.perform(get("/api/jobs/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsBadRequestForMalformedJobId() throws Exception {
        mockMvc.perform(get("/api/jobs/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getJobsByRecruiterId() throws Exception {
        UUID recruiterId = UUID.randomUUID();
        Job first = seedJob(recruiterId);
        Job second = seedJob(recruiterId);
        seedJob(UUID.randomUUID());

        mockMvc.perform(get("/api/jobs/recruiter/{recruiterId}", recruiterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id").value(containsInAnyOrder(
                        first.getId().toString(), second.getId().toString())));
    }

    @Test
    void returnsEmptyListForRecruiterWithoutJobs() throws Exception {
        mockMvc.perform(get("/api/jobs/recruiter/{recruiterId}", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void returnsBadRequestForMalformedRecruiterId() throws Exception {
        mockMvc.perform(get("/api/jobs/recruiter/{recruiterId}", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesJobAndReplacesSkills() throws Exception {
        Job job = seedJob(UUID.randomUUID());
        String payload = """
                {"title":"Staff Java Developer","location":"Munich","status":"CLOSED","skills":["Kafka","Docker"]}
                """;

        mockMvc.perform(put("/api/jobs/{id}", job.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Staff Java Developer"))
                .andExpect(jsonPath("$.location").value("Munich"))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.description").value("Build and maintain Spring Boot services"))
                .andExpect(jsonPath("$.employmentType").value("FULL_TIME"))
                .andExpect(jsonPath("$.experienceMin").value(3))
                .andExpect(jsonPath("$.skills[0]").value("Docker"))
                .andExpect(jsonPath("$.skills[1]").value("Kafka"));

        Job reloaded = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Staff Java Developer");
        assertThat(reloaded.getStatus()).isEqualTo(JobStatus.CLOSED);
        assertThat(jobSkillRepository.findByJobId(job.getId()))
                .extracting(JobSkill::getSkill)
                .containsExactly("Docker", "Kafka");
    }

    @Test
    void returnsNotFoundWhenUpdatingUnknownJob() throws Exception {
        mockMvc.perform(put("/api/jobs/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Staff Java Developer\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidUpdatePayload() throws Exception {
        Job job = seedJob(UUID.randomUUID());

        mockMvc.perform(put("/api/jobs/{id}", job.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experienceMin\":-2,\"skills\":[\"Java\",\" \"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUpdateViolatingExistingRange() throws Exception {
        Job job = seedJob(UUID.randomUUID());

        mockMvc.perform(put("/api/jobs/{id}", job.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experienceMin\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletesJob() throws Exception {
        Job job = seedJob(UUID.randomUUID());

        mockMvc.perform(delete("/api/jobs/{id}", job.getId()))
                .andExpect(status().isNoContent());

        assertThat(jobRepository.findById(job.getId())).isEmpty();
        assertThat(jobSkillRepository.findByJobId(job.getId())).isEmpty();

        mockMvc.perform(get("/api/jobs/{id}", job.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundWhenDeletingUnknownJob() throws Exception {
        mockMvc.perform(delete("/api/jobs/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private Job seedJob(UUID recruiterId) {
        Job job = new Job();
        job.setRecruiterId(recruiterId);
        job.setTitle("Senior Java Developer");
        job.setDescription("Build and maintain Spring Boot services");
        job.setLocation("Berlin");
        job.setEmploymentType(EmploymentType.FULL_TIME);
        job.setExperienceMin(3);
        job.setExperienceMax(6);
        job.setSalaryMin(60000);
        job.setSalaryMax(90000);
        job.setStatus(JobStatus.OPEN);
        Job saved = jobRepository.saveAndFlush(job);
        saveSkill(saved.getId(), "Java");
        saveSkill(saved.getId(), "Spring");
        return saved;
    }

    private void saveSkill(UUID jobId, String skill) {
        JobSkill jobSkill = new JobSkill();
        jobSkill.setJobId(jobId);
        jobSkill.setSkill(skill);
        jobSkillRepository.saveAndFlush(jobSkill);
    }

    private String createBody(UUID recruiterId) {
        return """
                {"recruiterId":"%s","title":"Senior Java Developer",
                "description":"Build and maintain Spring Boot services","location":"Berlin",
                "employmentType":"FULL_TIME","experienceMin":3,"experienceMax":6,
                "salaryMin":60000,"salaryMax":90000,"skills":["Java","Spring"]}
                """.formatted(recruiterId);
    }
}
