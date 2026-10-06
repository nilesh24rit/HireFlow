package com.hireflow.application.exception;

/**
 * Thrown when a request is syntactically readable but semantically invalid,
 * for example a missing required field or an unsupported status value.
 *
 * <p>Handled globally as {@code 400 Bad Request}.
 */
public class InvalidRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidRequestException(String message) {
        super(message);
    }
}
