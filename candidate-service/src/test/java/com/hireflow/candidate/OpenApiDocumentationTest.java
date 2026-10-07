package com.hireflow.candidate;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Verifies that the candidate service publishes a usable OpenAPI document on
 * {@code /v3/api-docs}: the Candidates tag, every candidate endpoint and the
 * shared {@code ApiErrorResponse} error contract.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class OpenApiDocumentationTest {

    private static final String DATABASE_NAME = "hireflow_candidate_openapi";

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void servesOpenApiDocumentWithServiceInfo() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("HireFlow Candidate Service API"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"));
    }

    @Test
    void documentsEveryCandidateEndpointUnderCandidatesTag() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/candidates']").exists())
                .andExpect(jsonPath("$.paths['/api/candidates'].post").exists())
                .andExpect(jsonPath("$.paths['/api/candidates/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/candidates/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/candidates/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/candidates/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/candidates/user/{userId}']").exists())
                .andExpect(jsonPath("$.paths['/api/candidates'].post.tags[0]").value("Candidates"))
                .andExpect(jsonPath("$.paths['/api/candidates'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/candidates/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/candidates'].post.responses['409']").exists());
    }

    @Test
    void documentsErrorsWithTheSharedErrorModel() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.timestamp").exists())
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.status").exists())
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.error").exists())
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.code").exists())
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.message").exists())
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.path").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/candidates'].post.responses['409'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/candidates/{id}'].get.responses['404'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/candidates'].post.responses['500'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"));
    }

    @Test
    void documentsRequestAndResponseSchemas() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.CreateCandidateRequest").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateCandidateRequest").exists())
                .andExpect(jsonPath("$.components.schemas.CandidateResponse").exists());
    }
}
