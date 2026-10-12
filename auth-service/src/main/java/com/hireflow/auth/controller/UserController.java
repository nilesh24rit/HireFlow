package com.hireflow.auth.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.hireflow.auth.dto.CreateUserRequest;
import com.hireflow.auth.dto.UpdateUserRequest;
import com.hireflow.auth.dto.UpdateUserRoleRequest;
import com.hireflow.auth.dto.UserResponse;
import com.hireflow.auth.error.ApiErrorResponse;
import com.hireflow.auth.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * REST endpoints for user management.
 *
 * <p>Every endpoint requires {@code Authorization: Bearer <token>} (the {@code 401}
 * contract is declared at the class level). The role and ownership rules themselves are
 * enforced by {@code @PreAuthorize} on the service methods, so they sit with the
 * operations they protect:</p>
 *
 * <ul>
 *   <li>create — any authenticated caller, always with the default CANDIDATE role;</li>
 *   <li>read / update by id — administrator or the owner of the record;</li>
 *   <li>directory lookup by email, delete, and role assignment — administrator only.</li>
 * </ul>
 *
 * <p>A refusal because of a role or ownership rule answers {@code 403 FORBIDDEN} with the
 * common HireFlow error contract — never {@code 401}, which is reserved for missing or
 * invalid credentials. The OpenAPI document below declares the {@code 403} on each
 * affected operation.</p>
 */
@RestController
@RequestMapping(value = "/api/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Users", description = "User management for the auth service. All endpoints require a "
        + "bearer token. Role rules: creating an account and reading or updating one's own record are "
        + "open to any authenticated caller (created accounts always receive the default CANDIDATE "
        + "role); directory lookup, deletion and role assignment are restricted to ADMIN. "
        + "401 = missing or invalid credentials, 403 = authenticated but not permitted.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class UserController {

    private static final String FORBIDDEN_DESCRIPTION =
            "Authenticated but not permitted: not an administrator, or not the owner of the record";

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a user", description = "Registers a new user with a unique email "
            + "address. Any authenticated caller may do this; the payload deliberately carries no role, "
            + "so the account is always created with the default CANDIDATE role. Roles are granted only "
            + "through PUT /api/users/{id}/role (ADMIN-only), which keeps self-privilege escalation "
            + "structurally impossible on this endpoint.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email is already registered",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.createUser(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user by id", description = "Returns the user identified by the given "
            + "UUID. Allowed for an administrator or for the authenticated user themselves: the token's "
            + "user id must match the path id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User found",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Malformed user id",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION,
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public UserResponse getUserById(
            @Parameter(description = "UUID of the user") @PathVariable UUID id) {
        return userService.getUserById(id);
    }

    @GetMapping("/email/{email}")
    @Operation(summary = "Get a user by email", description = "Returns the user registered with the "
            + "given email address. Administrator only: a directory lookup cannot be ownership-checked "
            + "against a principal that carries an id, so it is reserved for the ADMIN role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User found",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION,
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public UserResponse getUserByEmail(
            @Parameter(description = "Email address of the user") @PathVariable String email) {
        return userService.getUserByEmail(email);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a user", description = "Updates the user's profile information. "
            + "Allowed for an administrator or for the authenticated user themselves. "
            + "Security-sensitive fields such as email, role and enabled are not accepted by this "
            + "endpoint, so an owner can never change their own authority.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION,
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public UserResponse updateUser(
            @Parameter(description = "UUID of the user") @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return userService.updateUser(id, request);
    }

    @PutMapping(value = "/{id}/role", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Assign a role to a user", description = "Administrator only. This is the "
            + "single endpoint in HireFlow that changes a role: it accepts a dedicated payload "
            + "carrying just the new role and persists it immediately. Subsequent logins mint tokens "
            + "with the new role; tokens issued before the change keep the role they were signed with "
            + "until they expire, so the token lifetime is the invalidation window.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role assigned",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or unknown role in the payload",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an administrator",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public UserResponse assignUserRole(
            @Parameter(description = "UUID of the user whose role changes") @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRoleRequest request) {
        return userService.assignUserRole(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a user", description = "Removes the user identified by the given UUID. "
            + "Administrator only.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User deleted"),
            @ApiResponse(responseCode = "400", description = "Malformed user id",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an administrator",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public void deleteUser(
            @Parameter(description = "UUID of the user") @PathVariable UUID id) {
        userService.deleteUser(id);
    }
}
