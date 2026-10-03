package com.hireflow.job.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class JobRangeValidator implements ConstraintValidator<JobRangeValid, JobRanges> {

    @Override
    public boolean isValid(JobRanges value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return inOrder(value.experienceMin(), value.experienceMax())
                && inOrder(value.salaryMin(), value.salaryMax());
    }

    private boolean inOrder(Integer min, Integer max) {
        return min == null || max == null || min <= max;
    }
}
