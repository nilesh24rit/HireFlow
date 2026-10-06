package com.hireflow.auth.exception;

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

import com.hireflow.auth.controller.UserController;
import com.hireflow.auth.dto.CreateUserRequest;
import com.hireflow.auth.service.UserService;
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
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsNotFoundPayloadForMissingUser() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getUserById(id))
                .thenThrow(new ResourceNotFoundException("No user found with id '" + id + "'"));

        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No user found with id '" + id + "'"))
                .andExpect(jsonPath("$.path").value("/api/users/" + id))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andExpect(jsonPath("$.requestId").doesNotExist());
    }

    @Test
    void stampsErrorsWithUtcTimestamp() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getUserById(id)).thenThrow(new ResourceNotFoundException("No user found"));

        MvcResult result = mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isNotFound())
                .andReturn();

        String timestamp = JsonPath.read(result.getResponse().getContentAsString(), "$.timestamp");
        assertThat(timestamp).matches(ISO_UTC_TIMESTAMP);
    }

    @Test
    void echoesRequestIdForCorrelation() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getUserById(id)).thenThrow(new ResourceNotFoundException("No user found"));

        mockMvc.perform(get("/api/users/{id}", id).header("X-Request-Id", "corr-123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.requestId").value("corr-123"));
    }

    @Test
    void returnsConflictPayloadForDuplicateEmail() throws Exception {
        when(userService.createUser(any(CreateUserRequest.class)))
                .thenThrow(new DuplicateResourceException("A user with email 'jane@example.com' already exists"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.message").value("A user with email 'jane@example.com' already exists"))
                .andExpect(jsonPath("$.path").value("/api/users"));
    }

    @Test
    void returnsFieldLevelValidationErrors() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jane\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/users"))
                .andExpect(jsonPath("$.fieldErrors.email").value("email must not be blank"))
                .andExpect(jsonPath("$.fieldErrors.lastName").value("lastName must not be blank"))
                .andExpect(jsonPath("$.fieldErrors.role").value("role must not be null"));
    }

    @Test
    void returnsBadRequestForMalformedUserId() throws Exception {
        mockMvc.perform(get("/api/users/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed UUID 'not-a-uuid' for parameter 'id'"))
                .andExpect(jsonPath("$.path").value("/api/users/not-a-uuid"));
    }

    @Test
    void returnsBadRequestForUnknownRoleValue() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"firstName\":\"Jane\","
                                + "\"lastName\":\"Doe\",\"role\":\"SUPER_ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Unknown UserRole value 'SUPER_ADMIN'"))
                .andExpect(jsonPath("$.path").value("/api/users"));
    }

    @Test
    void returnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is malformed or unreadable"));
    }

    @Test
    void returnsUnsupportedMediaTypeWithCommonErrorPayload() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/users"));
    }

    @Test
    void returnsInternalServerErrorWithoutLeakingInternals() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getUserById(id))
                .thenThrow(new IllegalStateException("jdbc:postgresql://hireflow:secret@localhost/hireflow_auth"));

        MvcResult result = mockMvc.perform(get("/api/users/{id}", id))
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
                .doesNotContain("com.hireflow.auth.exception.GlobalExceptionHandler");
    }

    @Test
    void translatesKnownUniqueConstraintViolationIntoConflict() throws Exception {
        when(userService.createUser(any(CreateUserRequest.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement; SQL [insert into users (email) values (?)]; "
                                + "constraint [uk_users_email]",
                        new RuntimeException(
                                "ERROR: duplicate key value violates unique constraint \"uk_users_email\"")));

        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.message").value("A user with this email already exists"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("uk_users_email")
                .doesNotContain("insert into")
                .doesNotContain("SQL")
                .doesNotContain("duplicate key");
    }

    @Test
    void translatesUnknownConstraintViolationIntoConflict() throws Exception {
        when(userService.createUser(any(CreateUserRequest.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement; SQL [alter table users add column legacy boolean]"));

        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody()))
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

    private String createUserBody() {
        return "{\"email\":\"jane@example.com\",\"firstName\":\"Jane\","
                + "\"lastName\":\"Doe\",\"role\":\"CANDIDATE\"}";
    }
}
