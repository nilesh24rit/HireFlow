package com.hireflow.candidate.controller;

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

import com.hireflow.candidate.dto.CandidateResponse;
import com.hireflow.candidate.dto.CreateCandidateRequest;
import com.hireflow.candidate.dto.UpdateCandidateRequest;
import com.hireflow.candidate.error.ApiErrorResponse;
import com.hireflow.candidate.service.CandidateService;

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
 * REST endpoints for candidate profile management.
 *
 * <p>Every endpoint requires {@code Authorization: Bearer <token>}.
 * Role and ownership rules are enforced by {@code @PreAuthorize} on
 * {@link CandidateService} methods.</p>
 *
 * <ul>
 *   <li><b>Create</b> — CANDIDATE or ADMIN. For candidates, the authenticated user id is always
 *       used as the profile owner.</li>
 *   <li><b>Read by candidate id</b> — any authenticated caller.</li>
 *   <li><b>Read by user id</b> — own profile (CANDIDATE), or RECRUITER/ADMIN.</li>
 *   <li><b>Update</b> — CANDIDATE (own profile only) or ADMIN.</li>
 *   <li><b>Delete</b> — ADMIN only.</li>
 * </ul>
 *
 * <p>Authentication failures respond with {@code 401}; authorization failures
 * with {@code 403}. Both follow the common HireFlow error contract.</p>
 */
@RestController
@RequestMapping(value = "/api/candidates", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Candidates", description = "Candidate profile management. All endpoints require a bearer "
        + "token. Role rules: create and update require CANDIDATE or ADMIN (ownership enforced for "
        + "CANDIDATE); read by candidate-id is open to any authenticated caller; read by user-id "
        + "requires own profile (CANDIDATE) or RECRUITER/ADMIN; delete requires ADMIN. "
        + "401 = missing or invalid credentials, 403 = authenticated but not permitted.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class CandidateController {

    private final CandidateService candidateService;

    public CandidateController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a candidate profile",
            description = "Creates a candidate profile. "
                    + "Each user may own at most one candidate profile. "
                    + "Allowed for CANDIDATE and ADMIN roles. "
                    + "For CANDIDATE callers, the authenticated user's id is always used as the profile owner.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Candidate created",
                    content = @Content(schema = @Schema(implementation = CandidateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not a CANDIDATE or ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A candidate profile already exists for the user",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public ResponseEntity<CandidateResponse> createCandidate(
            @Valid @RequestBody CreateCandidateRequest request) {
        CandidateResponse created = candidateService.createCandidate(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a candidate by id",
            description = "Returns the candidate profile identified by the given UUID, "
                    + "including its skills. Allowed for any authenticated caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Candidate found",
                    content = @Content(schema = @Schema(implementation = CandidateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Malformed candidate id",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Candidate not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public CandidateResponse getCandidateById(
            @Parameter(description = "UUID of the candidate") @PathVariable UUID id) {
        return candidateService.getCandidateById(id);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get a candidate by user id",
            description = "Returns the candidate profile owned by the given user. "
                    + "CANDIDATE may only access their own profile (userId must match the token). "
                    + "RECRUITER and ADMIN may access any profile.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Candidate found",
                    content = @Content(schema = @Schema(implementation = CandidateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Malformed user id",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "CANDIDATE accessing another user's profile",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No candidate exists for the user",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public CandidateResponse getCandidateByUserId(
            @Parameter(description = "UUID of the owning user") @PathVariable UUID userId) {
        return candidateService.getCandidateByUserId(userId);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a candidate profile",
            description = "Updates the candidate profile. Omitted or null fields are left unchanged. "
                    + "When skills are supplied, they replace the current skill set. "
                    + "CANDIDATE may only update their own profile; ADMIN may update any profile.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Candidate updated",
                    content = @Content(schema = @Schema(implementation = CandidateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not CANDIDATE/ADMIN, or not the owner",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Candidate not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public CandidateResponse updateCandidate(
            @Parameter(description = "UUID of the candidate") @PathVariable UUID id,
            @Valid @RequestBody UpdateCandidateRequest request) {
        return candidateService.updateCandidate(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a candidate profile",
            description = "Removes the candidate profile identified by the given UUID, "
                    + "along with its skills. ADMIN only.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Candidate deleted"),
            @ApiResponse(responseCode = "400", description = "Malformed candidate id",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Candidate not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public void deleteCandidate(
            @Parameter(description = "UUID of the candidate") @PathVariable UUID id) {
        candidateService.deleteCandidate(id);
    }
}
