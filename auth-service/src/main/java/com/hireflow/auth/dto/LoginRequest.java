package com.hireflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload of the {@code POST /api/auth/login} credential login.
 *
 * <p>Only the raw email and password are accepted; the server looks the account up by
 * email and verifies the password against the stored hash. Identity, role and any token
 * claim are never taken from this payload.</p>
 */
@Schema(description = "Payload used to exchange email and password for an access token")
public record LoginRequest(
        @NotBlank(message = "email must not be blank")
        @Email(message = "email must be a valid email address")
        @Size(max = 320, message = "email must be at most 320 characters")
        @Schema(description = "Email address of the account",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "jane.doe@example.com")
        String email,
        @NotBlank(message = "password must not be blank")
        @Size(max = 72, message = "password must be at most 72 characters")
        @Schema(description = "Password of the account; verified against the stored hash",
                accessMode = Schema.AccessMode.WRITE_ONLY,
                requiredMode = Schema.RequiredMode.REQUIRED, example = "S3cure-Pass!")
        String password) {

    /**
     * Explicit {@code toString} so a {@link LoginRequest} can never leak the submitted
     * password into logs, debuggers or error messages.
     */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=[PROTECTED]]";
    }
}
