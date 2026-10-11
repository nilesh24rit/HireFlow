package com.hireflow.auth.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hireflow.auth.dto.LoginRequest;
import com.hireflow.auth.dto.LoginResponse;
import com.hireflow.auth.dto.OAuthHandoffRequest;
import com.hireflow.auth.error.ApiErrorResponse;
import com.hireflow.auth.exception.InvalidCredentialsException;
import com.hireflow.auth.security.OAuthHandoffService;
import com.hireflow.auth.service.AuthenticationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * The two ways a caller obtains a HireFlow access token, in one place so they stay one
 * contract rather than two:
 *
 * <ul>
 *   <li>{@code POST /api/auth/login} verifies an email and password and issues a bearer
 *       access token;</li>
 *   <li>{@code POST /api/auth/oauth2/token} completes a Google sign-in by redeeming the
 *       single-use handoff code the OAuth2 callback returned, for the very same
 *       {@link LoginResponse}.</li>
 * </ul>
 *
 * <p>Neither endpoint exposes a user record nor echoes the credential it was given, and
 * both derive identity and role entirely server-side — from the verified password or from
 * the handoff the callback created, never from anything the caller asserted.</p>
 */
@RestController
@RequestMapping(value = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Authentication", description = "Ways to obtain an access token for the auth service")
public class AuthController {

    /** Refuses an unknown, already-used or expired handoff code with a single generic message. */
    private static final String INVALID_HANDOFF_MESSAGE = "Invalid or expired sign-in handoff code";

    private final AuthenticationService authenticationService;
    private final OAuthHandoffService oauthHandoffService;

    public AuthController(AuthenticationService authenticationService,
            OAuthHandoffService oauthHandoffService) {
        this.authenticationService = authenticationService;
        this.oauthHandoffService = oauthHandoffService;
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Log in with email and password",
            description = "Verifies the credentials and, on success, issues a signed JWT "
                    + "access token to be used as 'Authorization: Bearer <token>'.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credentials verified; token issued",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Credentials rejected",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authenticationService.login(request);
    }

    @PostMapping(value = "/oauth2/token", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Exchange a Google sign-in handoff code for an access token",
            description = "Completes a Google OAuth2 sign-in. The callback at "
                    + "/login/oauth2/code/google answers with a single-use handoff code; "
                    + "redeeming it here returns the same signed JWT access token that "
                    + "password login issues. Each code works once and only briefly.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Handoff code redeemed; token issued",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Handoff code is unknown, already "
                    + "used or expired",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public LoginResponse exchangeOAuthHandoff(@Valid @RequestBody OAuthHandoffRequest request) {
        OAuthHandoffService.Handoff handoff = oauthHandoffService.redeem(request.handoffCode())
                .orElseThrow(() -> new InvalidCredentialsException(INVALID_HANDOFF_MESSAGE));
        return new LoginResponse(handoff.accessToken().token(), LoginResponse.TOKEN_TYPE,
                handoff.accessToken().expiresInSeconds());
    }
}
