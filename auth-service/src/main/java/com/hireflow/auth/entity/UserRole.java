package com.hireflow.auth.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Role assigned to a registered user. The role decides which parts of the
 * platform the account may use.
 */
@Schema(description = "Role of a registered user in HireFlow",
        enumAsRef = true,
        allowableValues = { "CANDIDATE", "RECRUITER", "ADMIN" })
public enum UserRole {

    /** A job seeker who owns a candidate profile and applications. */
    CANDIDATE,

    /** A hiring manager who owns jobs and reviews applications. */
    RECRUITER,

    /** An administrator of the platform. */
    ADMIN
}
