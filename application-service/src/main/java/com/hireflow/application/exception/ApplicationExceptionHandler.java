package com.hireflow.application.exception;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import tools.jackson.databind.exc.InvalidFormatException;

@RestControllerAdvice
public class ApplicationExceptionHandler {

    @ExceptionHandler(ApplicationNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleApplicationNotFound(ApplicationNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicateApplicationException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateApplication(DuplicateApplicationException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ApplicationValidationException.class)
    public ResponseEntity<ProblemDetail> handleApplicationValidation(ApplicationValidationException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> {
                    String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getCode();
                    return field + ": " + error.getDefaultMessage();
                })
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Request validation failed");
        problem.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST,
                "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof InvalidFormatException invalidFormat
                && invalidFormat.getTargetType() != null
                && invalidFormat.getTargetType().isEnum()) {
            return problem(HttpStatus.BAD_REQUEST,
                    "Unknown status value '" + invalidFormat.getValue() + "'");
        }
        return problem(HttpStatus.BAD_REQUEST, "Request body is malformed or unreadable");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        return ResponseEntity.status(status).body(problem);
    }
}
