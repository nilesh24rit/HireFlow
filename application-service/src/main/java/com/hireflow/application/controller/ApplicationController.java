package com.hireflow.application.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.hireflow.application.dto.ApplicationResponse;
import com.hireflow.application.dto.CreateApplicationRequest;
import com.hireflow.application.dto.UpdateApplicationStatusRequest;
import com.hireflow.application.error.ApiErrorResponse;
import com.hireflow.application.service.ApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * REST endpoints for job application management.
 *
 * <p>Every endpoint requires {@code Authorization: Bearer <token>}.
 * Role and ownership rules are enforced by {@code @PreAuthorize} on
 * {@link ApplicationService} methods.</p>
 *
 * <ul>
 *   <li><b>Create</b> — CANDIDATE (own candidate profile only) or ADMIN.</li>
 *   <li><b>Read by application id</b> — CANDIDATE (own application), RECRUITER (own jobs), or ADMIN.</li>
 *   <li><b>Read by candidate id</b> — CANDIDATE (own applications only) or ADMIN.</li>
 *   <li><b>Read by job id</b> — RECRUITER (own jobs only) or ADMIN.</li>
 *   <li><b>Update status</b> — RECRUITER (own jobs only) or ADMIN.</li>
 *   <li><b>Delete (withdraw)</b> — CANDIDATE (own application only) or ADMIN.</li>
 * </ul>
 *
 * <p>Authentication failures respond with {@code 401}; authorization failures
 * with {@code 403}. Both follow the common HireFlow error contract.</p>
 */
@RestController
@RequestMapping(value = "/api/applications", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Applications", description = "Candidate job application lifecycle management. All endpoints "
        + "require a bearer token. Role rules: create and candidate lookups require CANDIDATE (own profile) "
        + "or ADMIN; job applications and status updates require RECRUITER (own jobs) or ADMIN; "
        + "401 = missing or invalid credentials, 403 = authenticated but not permitted.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create an application",
            description = "Submits an application for a candidate to a job. "
                    + "Allowed for CANDIDATE (own profile) and ADMIN roles. "
                    + "The application always starts with the APPLIED status and a candidate "
                    + "may only apply to the same job once.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Application created",
                    content = @Content(schema = @Schema(implementation = ApplicationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not permitted or not candidate owner",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Candidate already applied to the job",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public ResponseEntity<ApplicationResponse> createApplication(
            @Valid @RequestBody CreateApplicationRequest request) {
        ApplicationResponse created = applicationService.createApplication(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an application by id",
            description = "Returns the application identified by the given UUID. "
                    + "Accessible by the owning CANDIDATE, the job's RECRUITER, or ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application found",
                    content = @Content(schema = @Schema(implementation = ApplicationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Malformed application id",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied to application",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Application not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public ApplicationResponse getApplicationById(
            @Parameter(description = "UUID of the application") @PathVariable UUID id) {
        return applicationService.getApplicationById(id);
    }

    @GetMapping("/candidate/{candidateId}")
    @Operation(summary = "Get applications by candidate id",
            description = "Returns all applications submitted by the given candidate. "
                    + "CANDIDATE may only access their own applications; ADMIN may access any.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Applications found (possibly an empty list)",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApplicationResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Malformed candidate id",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not candidate owner or not ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public List<ApplicationResponse> getApplicationsByCandidateId(
            @Parameter(description = "UUID of the candidate") @PathVariable UUID candidateId) {
        return applicationService.getApplicationsByCandidateId(candidateId);
    }

    @GetMapping("/job/{jobId}")
    @Operation(summary = "Get applications by job id",
            description = "Returns all applications received by the given job. "
                    + "RECRUITER may only access applications for their own jobs; ADMIN may access any.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Applications found (possibly an empty list)",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApplicationResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Malformed job id",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not job owner or not ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public List<ApplicationResponse> getApplicationsByJobId(
            @Parameter(description = "UUID of the job") @PathVariable UUID jobId) {
        return applicationService.getApplicationsByJobId(jobId);
    }

    @PatchMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update an application status",
            description = "Moves the application to the supplied status. "
                    + "RECRUITER may only update status for jobs they own; ADMIN may update any.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated",
                    content = @Content(schema = @Schema(implementation = ApplicationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing, unknown or invalid status",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not job owner or not ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Application not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public ApplicationResponse updateApplicationStatus(
            @Parameter(description = "UUID of the application") @PathVariable UUID id,
            @Valid @RequestBody UpdateApplicationStatusRequest request) {
        return applicationService.updateApplicationStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an application",
            description = "Removes the application identified by the given UUID, representing a withdrawal. "
                    + "CANDIDATE may only withdraw their own application; ADMIN may delete any.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Application deleted"),
            @ApiResponse(responseCode = "400", description = "Malformed application id",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not application owner or not ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Application not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public void deleteApplication(
            @Parameter(description = "UUID of the application") @PathVariable UUID id) {
        applicationService.deleteApplication(id);
    }
}
