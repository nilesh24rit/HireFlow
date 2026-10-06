package com.hireflow.job.exception;

/**
 * Thrown when a resource that the request refers to does not exist.
 *
 * <p>Handled globally as {@code 404 Not Found}. One common exception covers
 * every "not found" case (missing user, job, candidate, application, ...) so
 * that all services answer with the same error contract.
 */
public class ResourceNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
