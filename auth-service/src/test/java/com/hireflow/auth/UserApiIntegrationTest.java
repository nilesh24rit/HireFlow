package com.hireflow.auth;

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

import com.hireflow.auth.entity.User;
import com.hireflow.auth.entity.UserRole;
import com.hireflow.auth.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class UserApiIntegrationTest {

    private static final String DATABASE_NAME = "hireflow_auth";

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
    private UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void createsUser() throws Exception {
        String email = "create-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody(email, "Jane", "Doe", "CANDIDATE"))
                        .with(user("api-test"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.role").value("CANDIDATE"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void rejectsDuplicateEmailOnCreate() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@example.com";
        seedUser(email, UserRole.CANDIDATE);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody(email, "Other", "User", "RECRUITER"))
                        .with(user("api-test"))
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidCreatePayloads() throws Exception {
        List<String> invalidPayloads = List.of(
                "{\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"role\":\"CANDIDATE\"}",
                "{\"email\":\"not-an-email\",\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"role\":\"CANDIDATE\"}",
                "{\"email\":\"invalid-1@example.com\",\"firstName\":\" \",\"lastName\":\"Doe\",\"role\":\"CANDIDATE\"}",
                "{\"email\":\"invalid-2@example.com\",\"firstName\":\"Jane\",\"lastName\":\" \",\"role\":\"CANDIDATE\"}",
                "{\"email\":\"invalid-3@example.com\",\"firstName\":\"Jane\",\"lastName\":\"Doe\"}",
                "{\"email\":\"invalid-4@example.com\",\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"role\":\"SUPER_ADMIN\"}");

        for (String payload : invalidPayloads) {
            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload)
                            .with(user("api-test"))
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void getUserById() throws Exception {
        User user = seedUser("get-by-id-" + UUID.randomUUID() + "@example.com", UserRole.CANDIDATE);

        mockMvc.perform(get("/api/users/{id}", user.getId()).with(user("api-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.role").value("CANDIDATE"));
    }

    @Test
    void returnsNotFoundForUnknownUserId() throws Exception {
        mockMvc.perform(get("/api/users/{id}", UUID.randomUUID()).with(user("api-test")))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUserByEmail() throws Exception {
        User user = seedUser("get-by-email-" + UUID.randomUUID() + "@example.com", UserRole.RECRUITER);

        mockMvc.perform(get("/api/users/by-email/{email}", user.getEmail()).with(user("api-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.role").value("RECRUITER"));
    }

    @Test
    void returnsNotFoundForUnknownUserEmail() throws Exception {
        mockMvc.perform(get("/api/users/by-email/{email}", "missing-" + UUID.randomUUID() + "@example.com")
                        .with(user("api-test")))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateUserProfile() throws Exception {
        User user = seedUser("update-" + UUID.randomUUID() + "@example.com", UserRole.CANDIDATE);
        String payload = """
                {"firstName":"Janet","lastName":"Smith","email":"changed@example.com","role":"ADMIN","enabled":false}
                """;

        mockMvc.perform(put("/api/users/{id}", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(user("api-test"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Janet"))
                .andExpect(jsonPath("$.lastName").value("Smith"))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.role").value("CANDIDATE"))
                .andExpect(jsonPath("$.enabled").value(true));

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getFirstName()).isEqualTo("Janet");
        assertThat(reloaded.getEmail()).isEqualTo(user.getEmail());
    }

    @Test
    void rejectsUpdateWithBlankNames() throws Exception {
        User user = seedUser("update-invalid-" + UUID.randomUUID() + "@example.com", UserRole.CANDIDATE);

        mockMvc.perform(put("/api/users/{id}", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\" \",\"lastName\":\"Smith\"}")
                        .with(user("api-test"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundWhenUpdatingUnknownUser() throws Exception {
        mockMvc.perform(put("/api/users/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Janet\",\"lastName\":\"Smith\"}")
                        .with(user("api-test"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletesUser() throws Exception {
        User user = seedUser("delete-" + UUID.randomUUID() + "@example.com", UserRole.CANDIDATE);

        mockMvc.perform(delete("/api/users/{id}", user.getId())
                        .with(user("api-test"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(user.getId())).isEmpty();

        mockMvc.perform(get("/api/users/{id}", user.getId()).with(user("api-test")))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundWhenDeletingUnknownUser() throws Exception {
        mockMvc.perform(delete("/api/users/{id}", UUID.randomUUID())
                        .with(user("api-test"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    private User seedUser(String email, UserRole role) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Jane");
        user.setLastName("Doe");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private String createUserBody(String email, String firstName, String lastName, String role) {
        return """
                {"email":"%s","firstName":"%s","lastName":"%s","role":"%s"}
                """.formatted(email, firstName, lastName, role);
    }
}
