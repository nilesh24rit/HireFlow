package com.hireflow.auth.dto;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

import com.hireflow.auth.entity.UserRole;

import static org.assertj.core.api.Assertions.assertThat;

class UserRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsValidCreateUserRequest() {
        CreateUserRequest request = new CreateUserRequest("jane@example.com", "Jane", "Doe", UserRole.CANDIDATE);

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void rejectsMissingEmail() {
        CreateUserRequest request = new CreateUserRequest(null, "Jane", "Doe", UserRole.CANDIDATE);

        assertThat(violatedProperties(request)).contains("email");
    }

    @Test
    void rejectsBlankEmail() {
        CreateUserRequest request = new CreateUserRequest("   ", "Jane", "Doe", UserRole.CANDIDATE);

        assertThat(violatedProperties(request)).contains("email");
    }

    @Test
    void rejectsMalformedEmail() {
        CreateUserRequest request = new CreateUserRequest("not-an-email", "Jane", "Doe", UserRole.CANDIDATE);

        assertThat(violatedProperties(request)).contains("email");
    }

    @Test
    void rejectsBlankFirstName() {
        CreateUserRequest request = new CreateUserRequest("jane@example.com", " ", "Doe", UserRole.CANDIDATE);

        assertThat(violatedProperties(request)).contains("firstName");
    }

    @Test
    void rejectsBlankLastName() {
        CreateUserRequest request = new CreateUserRequest("jane@example.com", "Jane", " ", UserRole.CANDIDATE);

        assertThat(violatedProperties(request)).contains("lastName");
    }

    @Test
    void rejectsMissingRole() {
        CreateUserRequest request = new CreateUserRequest("jane@example.com", "Jane", "Doe", null);

        assertThat(violatedProperties(request)).contains("role");
    }

    @Test
    void rejectsOverlongEmail() {
        CreateUserRequest request = new CreateUserRequest(
                "a".repeat(309) + "@example.com", "Jane", "Doe", UserRole.CANDIDATE);

        assertThat(violatedProperties(request)).contains("email");
    }

    @Test
    void rejectsOverlongFirstName() {
        CreateUserRequest request = new CreateUserRequest(
                "jane@example.com", "x".repeat(101), "Doe", UserRole.CANDIDATE);

        assertThat(violatedProperties(request)).contains("firstName");
    }

    @Test
    void rejectsOverlongLastName() {
        CreateUserRequest request = new CreateUserRequest(
                "jane@example.com", "Jane", "x".repeat(101), UserRole.CANDIDATE);

        assertThat(violatedProperties(request)).contains("lastName");
    }

    @Test
    void acceptsValidUpdateUserRequest() {
        UpdateUserRequest request = new UpdateUserRequest("Janet", "Smith");

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void rejectsBlankFieldsOnUpdateUserRequest() {
        UpdateUserRequest request = new UpdateUserRequest(" ", null);

        assertThat(violatedProperties(request)).contains("firstName", "lastName");
    }

    @Test
    void rejectsOverlongNamesOnUpdateUserRequest() {
        UpdateUserRequest request = new UpdateUserRequest("x".repeat(101), "x".repeat(101));

        assertThat(violatedProperties(request)).contains("firstName", "lastName");
    }

    private Set<String> violatedProperties(Object target) {
        return VALIDATOR.validate(target).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
