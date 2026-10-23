package com.hireflow.ai.matching.client;

import org.springframework.http.HttpStatus;

public class MatchingDataUnavailableException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public MatchingDataUnavailableException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
