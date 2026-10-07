package com.hireflow.job.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Working arrangement offered by a job posting.
 */
@Schema(description = "Employment type offered by a job",
        enumAsRef = true,
        allowableValues = { "FULL_TIME", "PART_TIME", "CONTRACT", "INTERNSHIP" })
public enum EmploymentType {

    /** Permanent full-time position. */
    FULL_TIME,

    /** Permanent part-time position. */
    PART_TIME,

    /** Fixed-term or freelance contract. */
    CONTRACT,

    /** Learning position for students or recent graduates. */
    INTERNSHIP
}
