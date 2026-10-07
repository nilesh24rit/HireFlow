package com.hireflow.job.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lifecycle status of a job posting. New jobs start as {@link #DRAFT} unless
 * another status is supplied explicitly.
 */
@Schema(description = "Lifecycle status of a job posting",
        enumAsRef = true,
        allowableValues = { "DRAFT", "OPEN", "CLOSED", "ARCHIVED" })
public enum JobStatus {

    /** Written but not yet published to candidates. */
    DRAFT,

    /** Published and accepting applications. */
    OPEN,

    /** No longer accepting applications. */
    CLOSED,

    /** Kept for historical reference only. */
    ARCHIVED
}
