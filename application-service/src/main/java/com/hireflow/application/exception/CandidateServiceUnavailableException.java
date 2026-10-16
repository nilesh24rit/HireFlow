package com.hireflow.application.exception;

public class CandidateServiceUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CandidateServiceUnavailableException() {
        super("Candidate Service is unavailable");
    }
}
