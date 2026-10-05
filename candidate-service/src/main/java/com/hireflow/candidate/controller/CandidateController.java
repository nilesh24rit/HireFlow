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
import com.hireflow.candidate.service.CandidateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping(value = "/api/candidates", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Candidates", description = "Candidate profile management for the candidate service")
public class CandidateController {

    private final CandidateService candidateService;

    public CandidateController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a candidate profile",
            description = "Creates a candidate profile for a user. Each user may own at most one candidate profile.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Candidate created",
                    content = @Content(schema = @Schema(implementation = CandidateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "409", description = "A candidate profile already exists for the user")})
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
            description = "Returns the candidate profile identified by the given UUID, including its skills.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Candidate found",
                    content = @Content(schema = @Schema(implementation = CandidateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Malformed candidate id"),
            @ApiResponse(responseCode = "404", description = "Candidate not found")})
    public CandidateResponse getCandidateById(
            @Parameter(description = "UUID of the candidate") @PathVariable UUID id) {
        return candidateService.getCandidateById(id);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get a candidate by user id",
            description = "Returns the candidate profile owned by the given user, if one exists.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Candidate found",
                    content = @Content(schema = @Schema(implementation = CandidateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Malformed user id"),
            @ApiResponse(responseCode = "404", description = "No candidate exists for the user")})
    public CandidateResponse getCandidateByUserId(
            @Parameter(description = "UUID of the owning user") @PathVariable UUID userId) {
        return candidateService.getCandidateByUserId(userId);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a candidate profile",
            description = "Updates the candidate profile. Omitted or null fields are left unchanged. "
                    + "When skills are supplied, they replace the current skill set.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Candidate updated",
                    content = @Content(schema = @Schema(implementation = CandidateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "404", description = "Candidate not found")})
    public CandidateResponse updateCandidate(
            @Parameter(description = "UUID of the candidate") @PathVariable UUID id,
            @Valid @RequestBody UpdateCandidateRequest request) {
        return candidateService.updateCandidate(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a candidate profile",
            description = "Removes the candidate profile identified by the given UUID, along with its skills.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Candidate deleted"),
            @ApiResponse(responseCode = "400", description = "Malformed candidate id"),
            @ApiResponse(responseCode = "404", description = "Candidate not found")})
    public void deleteCandidate(
            @Parameter(description = "UUID of the candidate") @PathVariable UUID id) {
        candidateService.deleteCandidate(id);
    }
}
