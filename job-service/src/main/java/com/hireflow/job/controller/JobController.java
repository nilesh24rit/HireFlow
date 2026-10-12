package com.hireflow.job.controller;

import java.net.URI;
import java.util.List;
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

import com.hireflow.job.dto.CreateJobRequest;
import com.hireflow.job.dto.JobResponse;
import com.hireflow.job.dto.UpdateJobRequest;
import com.hireflow.job.error.ApiErrorResponse;
import com.hireflow.job.service.JobService;

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
 * REST endpoints for job requisition management.
 *
 * <p>Every endpoint requires {@code Authorization: Bearer <token>}.
 * Role and ownership rules are enforced by {@code @PreAuthorize} on
 * {@link JobService} methods.</p>
 *
 * <ul>
 *   <li><b>Create</b> — RECRUITER or ADMIN. For recruiters, the authenticated user id
 *       is always used as the job owner to prevent impersonation.</li>
 *   <li><b>Read by job id</b> — any authenticated caller (CANDIDATE, RECRUITER, ADMIN).</li>
 *   <li><b>Read by recruiter id</b> — any authenticated caller.</li>
 *   <li><b>Update</b> — RECRUITER (own jobs only) or ADMIN.</li>
 *   <li><b>Delete</b> — RECRUITER (own jobs only) or ADMIN.</li>
 * </ul>
 *
 * <p>Authentication failures respond with {@code 401}; authorization failures
 * with {@code 403}. Both follow the common HireFlow error contract.</p>
 */
@RestController
@RequestMapping(value = "/api/jobs", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Jobs", description = "Job requisition lifecycle management. All endpoints require a bearer "
        + "token. Role rules: create, update and delete require RECRUITER or ADMIN (ownership enforced for "
        + "RECRUITER); read operations are open to all authenticated callers. "
        + "401 = missing or invalid credentials, 403 = authenticated but not permitted.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a job",
            description = "Creates a job posting owned by a recruiter. Allowed for RECRUITER and ADMIN roles. "
                    + "For RECRUITER callers, recruiterId is derived from the authenticated user to prevent impersonation. "
                    + "When no status is supplied the job starts as DRAFT.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Job created",
                    content = @Content(schema = @Schema(implementation = JobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not a RECRUITER or ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Duplicate skill submitted for the job",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public ResponseEntity<JobResponse> createJob(@Valid @RequestBody CreateJobRequest request) {
        JobResponse created = jobService.createJob(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a job by id",
            description = "Returns the job identified by the given UUID, including its skills. "
                    + "Allowed for any authenticated caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job found",
                    content = @Content(schema = @Schema(implementation = JobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Malformed job id",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Job not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public JobResponse getJobById(
            @Parameter(description = "UUID of the job") @PathVariable UUID id) {
        return jobService.getJobById(id);
    }

    @GetMapping("/recruiter/{recruiterId}")
    @Operation(summary = "Get jobs by recruiter id",
            description = "Returns all jobs owned by the given recruiter. Allowed for any authenticated caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jobs found (possibly an empty list)",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Malformed recruiter id",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public List<JobResponse> getJobsByRecruiterId(
            @Parameter(description = "UUID of the owning recruiter") @PathVariable UUID recruiterId) {
        return jobService.getJobsByRecruiterId(recruiterId);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a job",
            description = "Updates the job. Omitted or null fields are left unchanged. "
                    + "When skills are supplied, they replace the current skill set. "
                    + "RECRUITER may only update jobs they own; ADMIN may update any job.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job updated",
                    content = @Content(schema = @Schema(implementation = JobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not RECRUITER/ADMIN, or not the job owner",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Job not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Duplicate skill submitted for the job",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public JobResponse updateJob(
            @Parameter(description = "UUID of the job") @PathVariable UUID id,
            @Valid @RequestBody UpdateJobRequest request) {
        return jobService.updateJob(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a job",
            description = "Removes the job identified by the given UUID, along with its skills. "
                    + "RECRUITER may only delete jobs they own; ADMIN may delete any job.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Job deleted"),
            @ApiResponse(responseCode = "400", description = "Malformed job id",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not RECRUITER/ADMIN, or not the job owner",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Job not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    public void deleteJob(
            @Parameter(description = "UUID of the job") @PathVariable UUID id) {
        jobService.deleteJob(id);
    }
}
