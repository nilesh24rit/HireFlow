package com.hireflow.candidate.exception;

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

import com.hireflow.candidate.controller.CandidateController;
import com.hireflow.candidate.dto.CreateCandidateRequest;
import com.hireflow.candidate.service.CandidateService;
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
    private CandidateService candidateService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new CandidateController(candidateService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsNotFoundPayloadForMissingCandidate() throws Exception {
        UUID id = UUID.randomUUID();
        when(candidateService.getCandidateById(id))
                .thenThrow(new ResourceNotFoundException("No candidate found with id '" + id + "'"));

        mockMvc.perform(get("/api/candidates/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No candidate found with id '" + id + "'"))
                .andExpect(jsonPath("$.path").value("/api/candidates/" + id))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andExpect(jsonPath("$.requestId").doesNotExist());
    }

    @Test
    void stampsErrorsWithUtcTimestamp() throws Exception {
        UUID id = UUID.randomUUID();
        when(candidateService.getCandidateById(id)).thenThrow(new ResourceNotFoundException("No candidate found"));

        MvcResult result = mockMvc.perform(get("/api/candidates/{id}", id))
                .andExpect(status().isNotFound())
                .andReturn();

        String timestamp = JsonPath.read(result.getResponse().getContentAsString(), "$.timestamp");
        assertThat(timestamp).matches(ISO_UTC_TIMESTAMP);
    }

    @Test
    void echoesRequestIdForCorrelation() throws Exception {
        UUID id = UUID.randomUUID();
        when(candidateService.getCandidateById(id)).thenThrow(new ResourceNotFoundException("No candidate found"));

        mockMvc.perform(get("/api/candidates/{id}", id).header("X-Request-Id", "corr-456"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.requestId").value("corr-456"));
    }

    @Test
    void returnsConflictPayloadForDuplicateCandidate() throws Exception {
        UUID userId = UUID.randomUUID();
        when(candidateService.createCandidate(any(CreateCandidateRequest.class)))
                .thenThrow(new DuplicateResourceException(
                        "A candidate profile already exists for user '" + userId + "'"));

        mockMvc.perform(post("/api/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(userId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.message").value("A candidate profile already exists for user '" + userId + "'"))
                .andExpect(jsonPath("$.path").value("/api/candidates"));
    }

    @Test
    void returnsFieldLevelValidationErrors() throws Exception {
        mockMvc.perform(post("/api/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"Senior Java Developer\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/candidates"))
                .andExpect(jsonPath("$.fieldErrors.userId").value("userId must not be null"));
    }

    @Test
    void returnsBadRequestForMalformedCandidateId() throws Exception {
        mockMvc.perform(get("/api/candidates/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed UUID 'not-a-uuid' for parameter 'id'"))
                .andExpect(jsonPath("$.path").value("/api/candidates/not-a-uuid"));
    }

    @Test
    void returnsBadRequestForUnreadableFieldValue() throws Exception {
        mockMvc.perform(post("/api/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\",\"yearsOfExperience\":\"five\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is malformed or unreadable"));
    }

    @Test
    void returnsUnsupportedMediaTypeWithCommonErrorPayload() throws Exception {
        mockMvc.perform(post("/api/candidates")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/candidates"));
    }

    @Test
    void returnsInternalServerErrorWithoutLeakingInternals() throws Exception {
        UUID id = UUID.randomUUID();
        when(candidateService.getCandidateById(id))
                .thenThrow(new IllegalStateException("jdbc:postgresql://hireflow:secret@localhost/hireflow_candidate"));

        MvcResult result = mockMvc.perform(get("/api/candidates/{id}", id))
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
                .doesNotContain("com.hireflow.candidate.exception.GlobalExceptionHandler");
    }

    @Test
    void translatesKnownUniqueConstraintViolationIntoConflict() throws Exception {
        UUID userId = UUID.randomUUID();
        when(candidateService.createCandidate(any(CreateCandidateRequest.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement; SQL [insert into candidates (user_id) values (?)]; "
                                + "constraint [uk_candidates_user_id]",
                        new RuntimeException(
                                "ERROR: duplicate key value violates unique constraint \"uk_candidates_user_id\"")));

        MvcResult result = mockMvc.perform(post("/api/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(userId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.message").value("A candidate profile already exists for this user"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("uk_candidates_user_id")
                .doesNotContain("insert into")
                .doesNotContain("SQL")
                .doesNotContain("duplicate key");
    }

    @Test
    void translatesKnownSkillConstraintViolationIntoConflict() throws Exception {
        UUID userId = UUID.randomUUID();
        when(candidateService.createCandidate(any(CreateCandidateRequest.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement; constraint [uk_candidate_skills_candidate_skill]",
                        new RuntimeException("ERROR: duplicate key value violates unique constraint "
                                + "\"uk_candidate_skills_candidate_skill\"")));

        mockMvc.perform(post("/api/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(userId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.message").value("A duplicate skill was submitted for the profile"));
    }

    @Test
    void translatesUnknownConstraintViolationIntoConflict() throws Exception {
        UUID userId = UUID.randomUUID();
        when(candidateService.createCandidate(any(CreateCandidateRequest.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement; SQL [alter table candidates add column legacy boolean]"));

        MvcResult result = mockMvc.perform(post("/api/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(userId)))
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

    private String createBody(UUID userId) {
        return "{\"userId\":\"" + userId + "\",\"headline\":\"Senior Java Developer\",\"skills\":[\"Java\"]}";
    }
}
