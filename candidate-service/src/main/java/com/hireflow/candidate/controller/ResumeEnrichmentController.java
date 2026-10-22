package com.hireflow.candidate.controller;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.hireflow.candidate.dto.ResumeConfirmationRequest;
import com.hireflow.candidate.dto.ResumeEnrichmentResponse;
import com.hireflow.candidate.service.ResumeEnrichmentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping(value = "/api/candidates/me/resume", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('CANDIDATE')")
public class ResumeEnrichmentController {

    private final ResumeEnrichmentService service;

    public ResumeEnrichmentController(ResumeEnrichmentService service) {
        this.service = service;
    }

    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResumeEnrichmentResponse process(Authentication authentication,
            @RequestHeader("Authorization") String authorization,
            @RequestPart("file") MultipartFile file) {
        return service.process(UUID.fromString(authentication.getName()), file, authorization);
    }

    @GetMapping("/enrichments/{enrichmentId}")
    public ResumeEnrichmentResponse get(Authentication authentication, @PathVariable UUID enrichmentId) {
        return service.get(UUID.fromString(authentication.getName()), enrichmentId);
    }

    @PostMapping("/enrichments/{enrichmentId}/confirm")
    public ResumeEnrichmentResponse confirm(Authentication authentication,
            @PathVariable UUID enrichmentId, @Valid @RequestBody ResumeConfirmationRequest request) {
        return service.confirm(UUID.fromString(authentication.getName()), enrichmentId, request);
    }

    @PostMapping("/enrichments/{enrichmentId}/reject")
    public ResumeEnrichmentResponse reject(Authentication authentication, @PathVariable UUID enrichmentId) {
        return service.reject(UUID.fromString(authentication.getName()), enrichmentId);
    }
}
