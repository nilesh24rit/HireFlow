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
    ADMIN;

    /**
     * The Spring Security authority granted to a user holding this role, always
     * {@code ROLE_} prefixed so that {@code hasRole(...)} expressions match it.
     *
     * @return the single authority of this role, for example {@code ROLE_CANDIDATE}
     */
    public String authority() {
        return "ROLE_" + name();
    }
}
