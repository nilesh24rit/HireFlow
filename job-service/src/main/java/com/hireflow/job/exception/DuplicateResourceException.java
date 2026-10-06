package com.hireflow.job.exception;

/**
 * Thrown when the request would create a resource that already exists, for
 * example a duplicate email or a duplicate skill.
 *
 * <p>Handled globally as {@code 409 Conflict}.
 */
public class DuplicateResourceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateResourceException(String message) {
        super(message);
    }
}
