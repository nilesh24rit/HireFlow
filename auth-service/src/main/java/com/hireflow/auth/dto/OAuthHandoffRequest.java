package com.hireflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload of {@code POST /api/auth/oauth2/token}, which turns the single-use handoff code
 * of a completed Google sign-in into the ordinary access token.
 *
 * <p>The code is the only credential accepted here; identity, role and any token claim are
 * derived server-side from the handoff the callback created, never from this payload.</p>
 */
@Schema(description = "Payload used to exchange a Google sign-in handoff code for an access token")
public record OAuthHandoffRequest(
        @NotBlank(message = "handoffCode must not be blank")
        @Size(max = 128, message = "handoffCode must be at most 128 characters")
        @Schema(description = "The one-time handoff code returned when the Google sign-in completed",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String handoffCode) {

    /**
     * Explicit {@code toString} so a handoff code can never leak into logs, debuggers or
     * error messages through incidental formatting.
     */
    @Override
    public String toString() {
        return "OAuthHandoffRequest[handoffCode=[PROTECTED]]";
    }
}
