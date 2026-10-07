package com.hireflow.application.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Status of a job application. Every new application starts as
 * {@link #APPLIED}; the client cannot choose the initial status.
 */
@Schema(description = "Status of a job application; new applications always start as APPLIED",
        enumAsRef = true,
        allowableValues = { "APPLIED", "UNDER_REVIEW", "SHORTLISTED", "INTERVIEW",
                "REJECTED", "HIRED", "WITHDRAWN" })
public enum ApplicationStatus {

    /** Submitted by the candidate; the initial status of every application. */
    APPLIED,

    /** A recruiter has started reviewing the application. */
    UNDER_REVIEW,

    /** The candidate advanced to the shortlist. */
    SHORTLISTED,

    /** An interview is scheduled or in progress. */
    INTERVIEW,

    /** The recruiter declined the application. */
    REJECTED,

    /** The candidate was hired for the job. */
    HIRED,

    /** Withdrawn by the candidate. */
    WITHDRAWN
}
