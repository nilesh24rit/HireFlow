package com.hireflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The success payload of a Google sign-in callback.
 *
 * <p>Deliberately carries no access token. The browser callback that produces this body
 * could leak a token into history, proxy logs and {@code Referer} headers, so what leaves
 * the callback is an opaque, single-use {@code handoffCode} instead. Redeeming it once at
 * {@code POST /api/auth/oauth2/token} returns the ordinary {@link LoginResponse}.</p>
 *
 * @param handoffCode one-time code standing in for the access token
 * @param expiresIn   seconds until the code stops being redeemable
 */
@Schema(description = "One-time handoff issued when a Google sign-in completes; no access token is included")
public record OAuthHandoffResponse(
        @Schema(description = "Single-use code, valid briefly and only once, to be exchanged "
                + "at POST /api/auth/oauth2/token for the access token. Never a token itself.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String handoffCode,
        @Schema(description = "Seconds until the handoff code expires",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "60")
        long expiresIn) {

    /**
     * Explicit {@code toString} so a handoff code — which briefly stands in for a token —
     * can never leak into logs, debuggers or error messages through incidental formatting.
     */
    @Override
    public String toString() {
        return "OAuthHandoffResponse[handoffCode=[PROTECTED], expiresIn=" + expiresIn + "]";
    }
}
