package com.hireflow.job.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

import com.hireflow.job.entity.EmploymentType;
import com.hireflow.job.entity.JobStatus;
import com.hireflow.job.validation.JobRangeValid;

import static org.assertj.core.api.Assertions.assertThat;

class JobRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsValidCreateJobRequest() {
        assertThat(VALIDATOR.validate(validCreateRequest())).isEmpty();
    }

    @Test
    void acceptsCreateJobRequestWithOptionalFieldsMissing() {
        CreateJobRequest request = new CreateJobRequest(
                UUID.randomUUID(), "Title", "Description", null, EmploymentType.FULL_TIME,
                null, null, null, null, null, null);

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void rejectsMissingRecruiterId() {
        CreateJobRequest request = new CreateJobRequest(
                null, "Title", "Description", null, EmploymentType.FULL_TIME,
                null, null, null, null, null, null);

        assertThat(violatedProperties(request)).contains("recruiterId");
    }

    @Test
    void rejectsBlankTitle() {
        CreateJobRequest request = new CreateJobRequest(
                UUID.randomUUID(), "   ", "Description", null, EmploymentType.FULL_TIME,
                null, null, null, null, null, null);

        assertThat(violatedProperties(request)).contains("title");
    }

    @Test
    void rejectsBlankDescription() {
        CreateJobRequest request = new CreateJobRequest(
                UUID.randomUUID(), "Title", "", null, EmploymentType.FULL_TIME,
                null, null, null, null, null, null);

        assertThat(violatedProperties(request)).contains("description");
    }

    @Test
    void rejectsMissingEmploymentType() {
        CreateJobRequest request = new CreateJobRequest(
                UUID.randomUUID(), "Title", "Description", null, null,
                null, null, null, null, null, null);

        assertThat(violatedProperties(request)).contains("employmentType");
    }

    @Test
    void rejectsNegativeExperienceValues() {
        CreateJobRequest request = new CreateJobRequest(
                UUID.randomUUID(), "Title", "Description", null, EmploymentType.FULL_TIME,
                -1, 5, null, null, null, null);

        assertThat(violatedProperties(request)).contains("experienceMin");
    }

    @Test
    void rejectsNegativeSalaryValues() {
        CreateJobRequest request = new CreateJobRequest(
                UUID.randomUUID(), "Title", "Description", null, EmploymentType.FULL_TIME,
                null, null, -100, null, null, null);

        assertThat(violatedProperties(request)).contains("salaryMin");
    }

    @Test
    void rejectsExperienceMinGreaterThanExperienceMax() {
        CreateJobRequest request = new CreateJobRequest(
                UUID.randomUUID(), "Title", "Description", null, EmploymentType.FULL_TIME,
                10, 5, null, null, null, null);

        assertThat(VALIDATOR.validate(request))
                .anySatisfy(violation -> assertThat(
                        violation.getConstraintDescriptor().getAnnotation().annotationType())
                        .isEqualTo(JobRangeValid.class));
    }

    @Test
    void rejectsSalaryMinGreaterThanSalaryMax() {
        CreateJobRequest request = new CreateJobRequest(
                UUID.randomUUID(), "Title", "Description", null, EmploymentType.FULL_TIME,
                null, null, 90000, 60000, null, null);

        assertThat(VALIDATOR.validate(request))
                .anySatisfy(violation -> assertThat(
                        violation.getConstraintDescriptor().getAnnotation().annotationType())
                        .isEqualTo(JobRangeValid.class));
    }

    @Test
    void rejectsBlankSkill() {
        CreateJobRequest request = new CreateJobRequest(
                UUID.randomUUID(), "Title", "Description", null, EmploymentType.FULL_TIME,
                null, null, null, null, null, List.of("Java", " "));

        assertThat(violatedProperties(request)).anyMatch(path -> path.startsWith("skills"));
    }

    @Test
    void rejectsOverlongSkill() {
        CreateJobRequest request = new CreateJobRequest(
                UUID.randomUUID(), "Title", "Description", null, EmploymentType.FULL_TIME,
                null, null, null, null, null, List.of("x".repeat(101)));

        assertThat(violatedProperties(request)).anyMatch(path -> path.startsWith("skills"));
    }

    @Test
    void acceptsValidUpdateJobRequest() {
        UpdateJobRequest request = new UpdateJobRequest(
                "Staff Java Developer", "Updated description", "Munich", EmploymentType.CONTRACT,
                5, 8, 80000, 120000, JobStatus.OPEN, List.of("Java", "Kafka"));

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void acceptsUpdateJobRequestWithAllFieldsMissing() {
        UpdateJobRequest request = new UpdateJobRequest(
                null, null, null, null, null, null, null, null, null, null);

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void rejectsNegativeExperienceOnUpdate() {
        UpdateJobRequest request = new UpdateJobRequest(
                null, null, null, null, -3, null, null, null, null, null);

        assertThat(violatedProperties(request)).contains("experienceMin");
    }

    @Test
    void rejectsInconsistentRangeOnUpdate() {
        UpdateJobRequest request = new UpdateJobRequest(
                null, null, null, null, 10, 5, null, null, null, null);

        assertThat(VALIDATOR.validate(request))
                .anySatisfy(violation -> assertThat(
                        violation.getConstraintDescriptor().getAnnotation().annotationType())
                        .isEqualTo(JobRangeValid.class));
    }

    @Test
    void rejectsBlankSkillOnUpdate() {
        UpdateJobRequest request = new UpdateJobRequest(
                null, null, null, null, null, null, null, null, null, List.of(" "));

        assertThat(violatedProperties(request)).anyMatch(path -> path.startsWith("skills"));
    }

    private CreateJobRequest validCreateRequest() {
        return new CreateJobRequest(
                UUID.randomUUID(),
                "Senior Java Developer",
                "Build and maintain Spring Boot services",
                "Berlin",
                EmploymentType.FULL_TIME,
                3,
                6,
                60000,
                90000,
                JobStatus.OPEN,
                List.of("Java", "Spring"));
    }

    private Set<String> violatedProperties(Object target) {
        return VALIDATOR.validate(target).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
