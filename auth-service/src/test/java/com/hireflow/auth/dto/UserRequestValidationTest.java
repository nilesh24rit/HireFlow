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

    private static final String VALID_PASSWORD = "Sup3r-Secret!";

    private static CreateUserRequest createRequest(String email, String firstName, String lastName,
            String password) {
        return new CreateUserRequest(email, firstName, lastName, password);
    }

    private static CreateUserRequest validCreateRequest() {
        return createRequest("jane@example.com", "Jane", "Doe", VALID_PASSWORD);
    }

    @Test
    void acceptsValidCreateUserRequest() {
        CreateUserRequest request = validCreateRequest();

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void rejectsMissingEmail() {
        CreateUserRequest request = createRequest(null, "Jane", "Doe", VALID_PASSWORD);

        assertThat(violatedProperties(request)).contains("email");
    }

    @Test
    void rejectsBlankEmail() {
        CreateUserRequest request = createRequest("   ", "Jane", "Doe", VALID_PASSWORD);

        assertThat(violatedProperties(request)).contains("email");
    }

    @Test
    void rejectsMalformedEmail() {
        CreateUserRequest request = createRequest("not-an-email", "Jane", "Doe", VALID_PASSWORD);

        assertThat(violatedProperties(request)).contains("email");
    }

    @Test
    void rejectsBlankFirstName() {
        CreateUserRequest request = createRequest("jane@example.com", " ", "Doe", VALID_PASSWORD);

        assertThat(violatedProperties(request)).contains("firstName");
    }

    @Test
    void rejectsBlankLastName() {
        CreateUserRequest request = createRequest("jane@example.com", "Jane", " ", VALID_PASSWORD);

        assertThat(violatedProperties(request)).contains("lastName");
    }

    @Test
    void rejectsMissingPassword() {
        CreateUserRequest request = createRequest("jane@example.com", "Jane", "Doe", null);

        assertThat(violatedProperties(request)).contains("password");
    }

    @Test
    void rejectsBlankPassword() {
        CreateUserRequest request = createRequest("jane@example.com", "Jane", "Doe", "   ");

        assertThat(violatedProperties(request)).contains("password");
    }

    @Test
    void rejectsTooShortPassword() {
        CreateUserRequest request = createRequest("jane@example.com", "Jane", "Doe", "Sh0rt!");

        assertThat(violatedProperties(request)).contains("password");
    }

    @Test
    void rejectsOverlongPassword() {
        CreateUserRequest request = createRequest(
                "jane@example.com", "Jane", "Doe", "x".repeat(73));

        assertThat(violatedProperties(request)).contains("password");
    }

    @Test
    void hasNoRoleFieldToValidateBecauseRoleIsNeverAcceptedOnCreate() {
        // The create payload is deliberately a four-field record: there is no role
        // component to be blank or missing, because registration can never choose one.
        CreateUserRequest request = validCreateRequest();

        assertThat(CreateUserRequest.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("role");
        assertThat(violatedProperties(request)).doesNotContain("role");
    }

    @Test
    void rejectsOverlongEmail() {
        CreateUserRequest request = createRequest(
                "a".repeat(309) + "@example.com", "Jane", "Doe", VALID_PASSWORD);

        assertThat(violatedProperties(request)).contains("email");
    }

    @Test
    void rejectsOverlongFirstName() {
        CreateUserRequest request = createRequest(
                "jane@example.com", "x".repeat(101), "Doe", VALID_PASSWORD);

        assertThat(violatedProperties(request)).contains("firstName");
    }

    @Test
    void rejectsOverlongLastName() {
        CreateUserRequest request = createRequest(
                "jane@example.com", "Jane", "x".repeat(101), VALID_PASSWORD);

        assertThat(violatedProperties(request)).contains("lastName");
    }

    @Test
    void neverExposesPasswordInToString() {
        // Records generate a toString over every component; the explicit override must
        // keep the raw password out of logs, debuggers and error messages.
        assertThat(validCreateRequest().toString())
                .doesNotContain(VALID_PASSWORD)
                .contains("[PROTECTED]");
    }

    @Test
    void acceptsValidLoginRequest() {
        LoginRequest request = new LoginRequest("jane@example.com", VALID_PASSWORD);

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void rejectsInvalidLoginRequests() {
        assertThat(violatedProperties(new LoginRequest(null, VALID_PASSWORD))).contains("email");
        assertThat(violatedProperties(new LoginRequest("   ", VALID_PASSWORD))).contains("email");
        assertThat(violatedProperties(new LoginRequest("not-an-email", VALID_PASSWORD))).contains("email");
        assertThat(violatedProperties(new LoginRequest("jane@example.com", null))).contains("password");
        assertThat(violatedProperties(new LoginRequest("jane@example.com", "   "))).contains("password");
        assertThat(violatedProperties(new LoginRequest("jane@example.com", "x".repeat(73)))).contains("password");
    }

    @Test
    void neverExposesPasswordInLoginRequestToString() {
        LoginRequest request = new LoginRequest("jane@example.com", VALID_PASSWORD);

        assertThat(request.toString())
                .doesNotContain(VALID_PASSWORD)
                .contains("[PROTECTED]");
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

    @Test
    void acceptsValidUpdateUserRoleRequest() {
        UpdateUserRoleRequest request = new UpdateUserRoleRequest(UserRole.RECRUITER);

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void rejectsMissingRoleOnUpdateUserRoleRequest() {
        // The dedicated role payload has exactly one field, and it may not be absent:
        // the ADMIN-only endpoint must receive an explicit, deliberate role choice.
        UpdateUserRoleRequest request = new UpdateUserRoleRequest(null);

        assertThat(violatedProperties(request)).contains("role");
    }

    private Set<String> violatedProperties(Object target) {
        return VALIDATOR.validate(target).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
