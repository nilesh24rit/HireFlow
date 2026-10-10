package com.hireflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Successful login response: a bearer access token plus its type and remaining lifetime.
 *
 * <p>Contains no user record, no password material and no signing details — only what a
 * client needs to call protected endpoints with {@code Authorization: Bearer <token>}.</p>
 */
@Schema(description = "Access token issued for successfully verified credentials")
public record LoginResponse(
        @Schema(description = "Signed JWT access token", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0MmFiY2UyLWUwOWItNGYwYS04YmMzLWQ1YmUxMjNlNDU2NyJ9.sig")
        String accessToken,
        @Schema(description = "Token type; always Bearer",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "Bearer")
        String tokenType,
        @Schema(description = "Seconds until the access token expires",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "3600")
        long expiresIn) {
}
