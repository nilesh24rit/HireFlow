package com.hireflow.application.dto;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

import com.hireflow.application.entity.ApplicationStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsValidCreateApplicationRequest() {
        CreateApplicationRequest request = new CreateApplicationRequest(
                UUID.randomUUID(), UUID.randomUUID(), "I would love to work on this role");

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void acceptsCreateApplicationRequestWithoutCoverLetter() {
        CreateApplicationRequest request = new CreateApplicationRequest(
                UUID.randomUUID(), UUID.randomUUID(), null);

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void rejectsMissingCandidateId() {
        CreateApplicationRequest request = new CreateApplicationRequest(
                null, UUID.randomUUID(), null);

        assertThat(violatedProperties(request)).contains("candidateId");
    }

    @Test
    void rejectsMissingJobId() {
        CreateApplicationRequest request = new CreateApplicationRequest(
                UUID.randomUUID(), null, null);

        assertThat(violatedProperties(request)).contains("jobId");
    }

    @Test
    void rejectsOverlongCoverLetter() {
        CreateApplicationRequest request = new CreateApplicationRequest(
                UUID.randomUUID(), UUID.randomUUID(), "x".repeat(5001));

        assertThat(violatedProperties(request)).contains("coverLetter");
    }

    @Test
    void acceptsValidStatusUpdateRequest() {
        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest(
                ApplicationStatus.UNDER_REVIEW);

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void rejectsMissingStatusOnUpdate() {
        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest(null);

        assertThat(violatedProperties(request)).contains("status");
    }

    private Set<String> violatedProperties(Object target) {
        return VALIDATOR.validate(target).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
