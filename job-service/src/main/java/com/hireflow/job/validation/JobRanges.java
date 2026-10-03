package com.hireflow.job.validation;

/**
 * Range fields shared by the job request payloads so that a single
 * class-level constraint can validate them.
 */
public interface JobRanges {

    Integer experienceMin();

    Integer experienceMax();

    Integer salaryMin();

    Integer salaryMax();
}
