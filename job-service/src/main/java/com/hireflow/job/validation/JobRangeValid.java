package com.hireflow.job.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Validates that a job payload keeps its experience and salary ranges ordered,
 * i.e. {@code experienceMin <= experienceMax} and {@code salaryMin <= salaryMax}
 * whenever both bounds are supplied.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = JobRangeValidator.class)
public @interface JobRangeValid {

    String message() default "Job ranges are not consistent";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
