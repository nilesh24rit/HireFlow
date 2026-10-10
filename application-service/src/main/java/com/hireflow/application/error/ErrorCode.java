package com.hireflow.application.error;

/**
 * Application error codes shared by every HireFlow API error response.
 *
 * <p>The codes are stable, machine readable values. Clients should branch on
 * the code instead of parsing the human readable message.
 */
public enum ErrorCode {

    /** Request payload failed bean validation. */
    VALIDATION_ERROR,

    /** The referenced resource does not exist. */
    RESOURCE_NOT_FOUND,

    /** The request would create a resource that already exists. */
    DUPLICATE_RESOURCE,

    /** The request itself is malformed or otherwise not acceptable. */
    INVALID_REQUEST,

    /** The supplied status or status transition is not valid. */
    INVALID_STATUS,

    /** The request conflicts with the current state of the resource. */
    CONFLICT,

    /** The request carries no valid credentials for a protected endpoint. */
    UNAUTHENTICATED,

    /** The caller is authenticated but not allowed to perform the request. */
    FORBIDDEN,

    /** An unexpected server side failure. */
    INTERNAL_ERROR
}
