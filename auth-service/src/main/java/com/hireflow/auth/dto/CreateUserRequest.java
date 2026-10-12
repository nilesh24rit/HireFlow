package com.hireflow.auth.dto;

import com.hireflow.auth.entity.UserRole;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload used to register a new HireFlow account.
 *
 * <p><b>The role is deliberately not accepted here.</b> Every account created through this
 * endpoint receives the fixed default {@link UserRole#CANDIDATE}: registration can never
 * choose, suggest or escalate a role, because there is no field to carry one. Roles are
 * assigned only by an administrator through the dedicated role endpoint
 * ({@code PUT /api/users/{id}/role}), which has its own DTO and its own ADMIN-only rule.
 * This is what makes self-privilege escalation through creation impossible rather than
 * merely filtered.</p>
 */
@Schema(description = "Payload used to register a new HireFlow account. The role is deliberately "
        + "not accepted: accounts created through this endpoint always receive the default CANDIDATE "
        + "role, and roles are assigned separately by an administrator.")
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
        String password) {

    /**
     * Explicit {@code toString} so a {@link CreateUserRequest} can never leak the raw
     * password into logs, debuggers or error messages. Passwords are excluded, never masked.
     */
    @Override
    public String toString() {
        return "CreateUserRequest[email=" + email + ", firstName=" + firstName
                + ", lastName=" + lastName + ", password=[PROTECTED]]";
    }
}
