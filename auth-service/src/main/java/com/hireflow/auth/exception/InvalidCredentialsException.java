package com.hireflow.auth.exception;

/**
 * Thrown when a credential login fails for any reason: unknown email, missing password
 * hash (legacy account), wrong password or disabled account.
 *
 * <p>A single exception type with a single generic message is deliberate: the login
 * response must not reveal whether an email address is registered. It is mapped to
 * {@code 401 Unauthorized} by the global exception handler.</p>
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
