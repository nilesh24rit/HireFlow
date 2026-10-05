package com.hireflow.candidate.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsValidCreateCandidateRequest() {
        assertThat(VALIDATOR.validate(validCreateRequest())).isEmpty();
    }

    @Test
    void acceptsCreateCandidateRequestWithOptionalFieldsMissing() {
        CreateCandidateRequest request = new CreateCandidateRequest(
                UUID.randomUUID(), null, null, null, null, null, null, null, null, null, null);

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void rejectsMissingUserId() {
        CreateCandidateRequest request = new CreateCandidateRequest(
                null, "Headline", null, null, null, null, null, null, null, null, null);

        assertThat(violatedProperties(request)).contains("userId");
    }

    @Test
    void rejectsOverlongHeadline() {
        CreateCandidateRequest request = new CreateCandidateRequest(
                UUID.randomUUID(), "x".repeat(201), null, null, null, null, null, null, null, null, null);

        assertThat(violatedProperties(request)).contains("headline");
    }

    @Test
    void rejectsOverlongSummary() {
        CreateCandidateRequest request = new CreateCandidateRequest(
                UUID.randomUUID(), null, "x".repeat(5001), null, null, null, null, null, null, null, null);

        assertThat(violatedProperties(request)).contains("summary");
    }

    @Test
    void rejectsOverlongLocation() {
        CreateCandidateRequest request = new CreateCandidateRequest(
                UUID.randomUUID(), null, null, "x".repeat(201), null, null, null, null, null, null, null);

        assertThat(violatedProperties(request)).contains("location");
    }

    @Test
    void rejectsNegativeYearsOfExperience() {
        CreateCandidateRequest request = new CreateCandidateRequest(
                UUID.randomUUID(), null, null, null, -1, null, null, null, null, null, null);

        assertThat(violatedProperties(request)).contains("yearsOfExperience");
    }

    @Test
    void rejectsMalformedResumeUrl() {
        CreateCandidateRequest request = new CreateCandidateRequest(
                UUID.randomUUID(), null, null, null, null, null, null, "not-a-url", null, null, null);

        assertThat(violatedProperties(request)).contains("resumeUrl");
    }

    @Test
    void rejectsMalformedGithubUrl() {
        CreateCandidateRequest request = new CreateCandidateRequest(
                UUID.randomUUID(), null, null, null, null, null, null, null, null, "github.com/jane", null);

        assertThat(violatedProperties(request)).contains("githubUrl");
    }

    @Test
    void rejectsBlankSkill() {
        CreateCandidateRequest request = new CreateCandidateRequest(
                UUID.randomUUID(), null, null, null, null, null, null, null, null, null,
                List.of("Java", " "));

        assertThat(violatedProperties(request)).anyMatch(path -> path.startsWith("skills"));
    }

    @Test
    void rejectsOverlongSkill() {
        CreateCandidateRequest request = new CreateCandidateRequest(
                UUID.randomUUID(), null, null, null, null, null, null, null, null, null,
                List.of("x".repeat(101)));

        assertThat(violatedProperties(request)).anyMatch(path -> path.startsWith("skills"));
    }

    @Test
    void rejectsOverlongResumeUrl() {
        CreateCandidateRequest request = new CreateCandidateRequest(
                UUID.randomUUID(), null, null, null, null, null, null,
                "https://example.com/" + "a".repeat(500), null, null, null);

        assertThat(violatedProperties(request)).contains("resumeUrl");
    }

    @Test
    void acceptsValidUpdateCandidateRequest() {
        UpdateCandidateRequest request = new UpdateCandidateRequest(
                "Staff Engineer", "Experienced backend engineer", "Munich", 7, "Acme", "Principal Engineer",
                "https://resume.example.com/jane.pdf", null, null, List.of("Java", "Kafka"));

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void acceptsUpdateCandidateRequestWithAllFieldsMissing() {
        UpdateCandidateRequest request = new UpdateCandidateRequest(
                null, null, null, null, null, null, null, null, null, null);

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void rejectsNegativeYearsOfExperienceOnUpdate() {
        UpdateCandidateRequest request = new UpdateCandidateRequest(
                null, null, null, -3, null, null, null, null, null, null);

        assertThat(violatedProperties(request)).contains("yearsOfExperience");
    }

    @Test
    void rejectsMalformedLinkedinUrlOnUpdate() {
        UpdateCandidateRequest request = new UpdateCandidateRequest(
                null, null, null, null, null, null, null, "linkedin.com/in/jane", null, null);

        assertThat(violatedProperties(request)).contains("linkedinUrl");
    }

    @Test
    void rejectsBlankSkillOnUpdate() {
        UpdateCandidateRequest request = new UpdateCandidateRequest(
                null, null, null, null, null, null, null, null, null, List.of(" "));

        assertThat(violatedProperties(request)).anyMatch(path -> path.startsWith("skills"));
    }

    private CreateCandidateRequest validCreateRequest() {
        return new CreateCandidateRequest(
                UUID.randomUUID(),
                "Senior Java Developer",
                "Experienced backend engineer",
                "Berlin",
                5,
                "Acme",
                "Software Engineer",
                "https://resume.example.com/jane.pdf",
                "https://linkedin.com/in/jane",
                "https://github.com/jane",
                List.of("Java", "Spring"));
    }

    private Set<String> violatedProperties(Object target) {
        return VALIDATOR.validate(target).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
