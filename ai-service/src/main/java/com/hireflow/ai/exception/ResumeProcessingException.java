package com.hireflow.ai.exception;

public class ResumeProcessingException extends RuntimeException {

    public ResumeProcessingException(String message) {
        super(message);
    }

    public ResumeProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
