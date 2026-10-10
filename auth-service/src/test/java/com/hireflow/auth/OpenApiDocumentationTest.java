package com.hireflow.auth;

import com.hireflow.auth.security.TestSigningKeys;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Verifies that the auth service publishes a usable OpenAPI document on
 * {@code /v3/api-docs}: the Users tag, every user endpoint and the shared
 * {@code ApiErrorResponse} error contract.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class OpenApiDocumentationTest {

    private static final String DATABASE_NAME = "hireflow_auth_openapi";

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void servesOpenApiDocumentWithServiceInfo() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(user("openapi-test")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("HireFlow Auth Service API"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"));
    }

    @Test
    void documentsEveryUserEndpointUnderUsersTag() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(user("openapi-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/users']").exists())
                .andExpect(jsonPath("$.paths['/api/users'].post").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/users/email/{email}']").exists())
                .andExpect(jsonPath("$.paths['/api/users'].post.tags[0]").value("Users"))
                .andExpect(jsonPath("$.paths['/api/users'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}'].get.responses['404']").exists());
    }

    @Test
    void documentsErrorsWithTheSharedErrorModel() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(user("openapi-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.timestamp").exists())
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.status").exists())
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.error").exists())
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.code").exists())
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.message").exists())
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.path").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/users'].post.responses['409'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/users/{id}'].get.responses['404'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/users'].post.responses['500'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"));
    }

    @Test
    void documentsRequestAndResponseSchemas() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(user("openapi-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.CreateUserRequest").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateUserRequest").exists())
                .andExpect(jsonPath("$.components.schemas.UserResponse").exists())
                .andExpect(jsonPath("$.components.schemas.UserRole").exists())
                .andExpect(jsonPath("$.components.schemas.UserRole.enum[0]").value("CANDIDATE"));
    }

    @Test
    void documentsLoginEndpointAndSchemas() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(user("openapi-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.tags[0]").value("Authentication"))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['401']").exists())
                .andExpect(jsonPath("$.components.schemas.LoginRequest").exists())
                .andExpect(jsonPath("$.components.schemas.LoginResponse").exists())
                .andExpect(jsonPath("$.components.schemas.LoginResponse.properties.accessToken").exists())
                .andExpect(jsonPath("$.components.schemas.LoginResponse.properties.tokenType").exists())
                .andExpect(jsonPath("$.components.schemas.LoginResponse.properties.expiresIn").exists())
                .andExpect(jsonPath("$.components.schemas.LoginRequest.properties.password.writeOnly")
                        .value(true))
                .andExpect(jsonPath("$.components.schemas.LoginResponse.properties.password").doesNotExist());
    }

    @Test
    void documentsBearerSecurityScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(user("openapi-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
    }

    @Test
    void requiresBearerTokenOnEveryProtectedUserOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(user("openapi-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/users'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/users/email/{email}'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}'].put.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}'].delete.security[0].bearerAuth").exists());
    }

    @Test
    void documents401OnProtectedUserOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(user("openapi-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/users'].post.responses['401']"
                        + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"))
                .andExpect(jsonPath("$.paths['/api/users/{id}'].get.responses['401']"
                        + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"))
                .andExpect(jsonPath("$.paths['/api/users/{id}'].put.responses['401']"
                        + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"))
                .andExpect(jsonPath("$.paths['/api/users/{id}'].delete.responses['401']"
                        + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"));
    }

    @Test
    void documentsLoginEndpointWithoutSecurityRequirement() throws Exception {
        // Login is the only unauthenticated endpoint: it must carry no bearer requirement.
        mockMvc.perform(get("/v3/api-docs").with(user("openapi-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").doesNotExist());
    }

    @Test
    void documentsPasswordAsWriteOnlyAndNeverInResponses() throws Exception {
        // The registration payload documents its password input as write-only, and no
        // response schema may carry password material of any kind.
        mockMvc.perform(get("/v3/api-docs").with(user("openapi-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.CreateUserRequest.properties.password").exists())
                .andExpect(jsonPath("$.components.schemas.CreateUserRequest.properties.password.writeOnly")
                        .value(true))
                .andExpect(jsonPath("$.components.schemas.UserResponse.properties.password").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UserResponse.properties.passwordHash").doesNotExist());

        mockMvc.perform(get("/v3/api-docs").with(user("openapi-test")))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("passwordHash")
                        .doesNotContain("$2a$")
                        .doesNotContain("Sup3r-Secret"));
    }
}
