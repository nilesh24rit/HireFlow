package com.hireflow.auth.dto;

import com.hireflow.auth.entity.UserRole;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload used to register a new user")
public record CreateUserRequest(
        @NotBlank(message = "email must not be blank")
        @Email(message = "email must be a valid email address")
        @Size(max = 320, message = "email must be at most 320 characters")
        @Schema(description = "Unique email address of the user",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "jane.doe@example.com")
        String email,
        @NotBlank(message = "firstName must not be blank")
        @Size(max = 100, message = "firstName must be at most 100 characters")
        @Schema(description = "First name of the user",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "Jane")
        String firstName,
        @NotBlank(message = "lastName must not be blank")
        @Size(max = 100, message = "lastName must be at most 100 characters")
        @Schema(description = "Last name of the user",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "Doe")
        String lastName,
        @NotBlank(message = "password must not be blank")
        @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
        @Schema(description = "Initial password for email-and-password login. "
                + "Stored only as a BCrypt hash; never returned by any endpoint.",
                accessMode = Schema.AccessMode.WRITE_ONLY,
                requiredMode = Schema.RequiredMode.REQUIRED, example = "S3cure-Pass!")
        String password,
        @NotNull(message = "role must not be null")
        @Schema(description = "Role assigned to the user",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "CANDIDATE")
        UserRole role) {

    /**
     * Explicit {@code toString} so a {@link CreateUserRequest} can never leak the raw
     * password into logs, debuggers or error messages. Passwords are excluded, never masked.
     */
    @Override
    public String toString() {
        return "CreateUserRequest[email=" + email + ", firstName=" + firstName
                + ", lastName=" + lastName + ", password=[PROTECTED], role=" + role + "]";
    }
}
