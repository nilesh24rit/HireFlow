package com.hireflow.ai.exception;

import org.springframework.http.HttpStatus;

public class ResumeProcessingException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ResumeProcessingException(String message) {
        this(HttpStatus.BAD_GATEWAY, "RESUME_PROCESSING_FAILED", message, null);
    }

    public ResumeProcessingException(String message, Throwable cause) {
        this(HttpStatus.BAD_GATEWAY, "RESUME_PROCESSING_FAILED", message, cause);
    }

    public ResumeProcessingException(HttpStatus status, String code, String message, Throwable cause) {
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
