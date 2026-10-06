package com.hireflow.job.exception;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.hireflow.job.controller.JobController;
import com.hireflow.job.dto.CreateJobRequest;
import com.hireflow.job.service.JobService;
import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Verifies the common error contract produced by the global exception handler.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private static final String ISO_UTC_TIMESTAMP = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z";

    @Mock
    private JobService jobService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new JobController(jobService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsNotFoundPayloadForMissingJob() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.getJobById(id))
                .thenThrow(new ResourceNotFoundException("No job found with id '" + id + "'"));

        mockMvc.perform(get("/api/jobs/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No job found with id '" + id + "'"))
                .andExpect(jsonPath("$.path").value("/api/jobs/" + id))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andExpect(jsonPath("$.requestId").doesNotExist());
    }

    @Test
    void stampsErrorsWithUtcTimestamp() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.getJobById(id)).thenThrow(new ResourceNotFoundException("No job found"));

        MvcResult result = mockMvc.perform(get("/api/jobs/{id}", id))
                .andExpect(status().isNotFound())
                .andReturn();

        String timestamp = JsonPath.read(result.getResponse().getContentAsString(), "$.timestamp");
        assertThat(timestamp).matches(ISO_UTC_TIMESTAMP);
    }

    @Test
    void echoesRequestIdForCorrelation() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.getJobById(id)).thenThrow(new ResourceNotFoundException("No job found"));

        mockMvc.perform(get("/api/jobs/{id}", id).header("X-Request-Id", "corr-789"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.requestId").value("corr-789"));
    }

    @Test
    void returnsConflictPayloadForDuplicateJobSkill() throws Exception {
        when(jobService.createJob(any(CreateJobRequest.class)))
                .thenThrow(new DuplicateResourceException("A duplicate skill was submitted for the job"));

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.message").value("A duplicate skill was submitted for the job"))
                .andExpect(jsonPath("$.path").value("/api/jobs"));
    }

    @Test
    void returnsFieldLevelValidationErrors() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recruiterId\":\"" + UUID.randomUUID()
                                + "\",\"description\":\"Build and maintain Spring Boot services\","
                                + "\"employmentType\":\"FULL_TIME\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/jobs"))
                .andExpect(jsonPath("$.fieldErrors.title").value("title must not be blank"));
    }

    @Test
    void returnsBadRequestForMalformedJobId() throws Exception {
        mockMvc.perform(get("/api/jobs/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed UUID 'not-a-uuid' for parameter 'id'"))
                .andExpect(jsonPath("$.path").value("/api/jobs/not-a-uuid"));
    }

    @Test
    void returnsBadRequestForUnknownJobStatus() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBodyWithStatus("UNKNOWN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"))
                .andExpect(jsonPath("$.message").value("Unknown JobStatus value 'UNKNOWN'"))
                .andExpect(jsonPath("$.path").value("/api/jobs"));
    }

    @Test
    void returnsBadRequestForUnknownEmploymentType() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recruiterId\":\"" + UUID.randomUUID() + "\",\"title\":\"Java Developer\","
                                + "\"description\":\"Build and maintain Spring Boot services\","
                                + "\"employmentType\":\"WIZARD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Unknown EmploymentType value 'WIZARD'"));
    }

    @Test
    void returnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is malformed or unreadable"));
    }

    @Test
    void returnsUnsupportedMediaTypeWithCommonErrorPayload() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/jobs"));
    }

    @Test
    void returnsInternalServerErrorWithoutLeakingInternals() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.getJobById(id))
                .thenThrow(new IllegalStateException("jdbc:postgresql://hireflow:secret@localhost/hireflow_job"));

        MvcResult result = mockMvc.perform(get("/api/jobs/{id}", id))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("jdbc:postgresql")
                .doesNotContain("secret")
                .doesNotContain("IllegalStateException")
                .doesNotContain("java.lang")
                .doesNotContain("com.hireflow.job.exception.GlobalExceptionHandler");
    }

    @Test
    void translatesKnownUniqueConstraintViolationIntoConflict() throws Exception {
        when(jobService.createJob(any(CreateJobRequest.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement; SQL [insert into job_skills (job_id, skill) values (?, ?)]; "
                                + "constraint [uk_job_skills_job_skill]",
                        new RuntimeException(
                                "ERROR: duplicate key value violates unique constraint \"uk_job_skills_job_skill\"")));

        MvcResult result = mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.message").value("A duplicate skill was submitted for the job"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("uk_job_skills_job_skill")
                .doesNotContain("insert into")
                .doesNotContain("SQL")
                .doesNotContain("duplicate key");
    }

    @Test
    void translatesUnknownConstraintViolationIntoConflict() throws Exception {
        when(jobService.createJob(any(CreateJobRequest.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement; SQL [alter table jobs add column legacy boolean]"));

        MvcResult result = mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("The request conflicts with existing data"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("alter table")
                .doesNotContain("SQL");
    }

    private String createBody() {
        return "{\"recruiterId\":\"" + UUID.randomUUID()
                + "\",\"title\":\"Senior Java Developer\","
                + "\"description\":\"Build and maintain Spring Boot services\","
                + "\"employmentType\":\"FULL_TIME\",\"skills\":[\"Java\"]}";
    }

    private String createBodyWithStatus(String status) {
        return "{\"recruiterId\":\"" + UUID.randomUUID()
                + "\",\"title\":\"Senior Java Developer\","
                + "\"description\":\"Build and maintain Spring Boot services\","
                + "\"employmentType\":\"FULL_TIME\",\"status\":\"" + status + "\"}";
    }
}
