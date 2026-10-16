package com.hireflow.application.exception;

public class CandidateServiceTimeoutException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CandidateServiceTimeoutException() {
        super("Candidate Service did not respond in time");
    }
}
