package com.hireflow.ai.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.hireflow.ai.matching.client.MatchingDataUnavailableException;
import com.hireflow.ai.matching.service.MatchingAuthorizationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidResumeException.class)
    ResponseEntity<Map<String, Object>> invalidResume(InvalidResumeException ex) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_RESUME", ex.getMessage());
    }

    @ExceptionHandler(ResumeProcessingException.class)
    ResponseEntity<Map<String, Object>> processingFailure(ResumeProcessingException ex) {
        return response(ex.status(), ex.code(),
                "Resume processing is temporarily unavailable");
    }

    @ExceptionHandler(MatchingAuthorizationException.class)
    ResponseEntity<Map<String, Object>> matchingAuthorization(MatchingAuthorizationException ex) {
        return response(ex.status(), "MATCHING_ACCESS_DENIED", ex.getMessage());
    }

    @ExceptionHandler(MatchingDataUnavailableException.class)
    ResponseEntity<Map<String, Object>> matchingDataFailure(MatchingDataUnavailableException ex) {
        return response(ex.status(), ex.code(), ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "code", code,
                "message", message));
    }
}
