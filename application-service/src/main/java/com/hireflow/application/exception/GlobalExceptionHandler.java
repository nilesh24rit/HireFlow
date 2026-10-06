package com.hireflow.application.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.hireflow.application.error.ApiErrorResponse;
import com.hireflow.application.error.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import tools.jackson.databind.exc.InvalidFormatException;

/**
 * Global exception handler translating every failure into the common
 * {@link ApiErrorResponse} JSON contract.
 *
 * <p>Controllers and services stay free of HTTP concerns: they simply throw,
 * and this advice maps the outcome onto a consistent status code, error code
 * and safe message. Stack traces, SQL and other implementation details are
 * logged server side only and never returned to the caller.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final int MAX_REQUEST_ID_LENGTH = 64;
    private static final int MAX_CAUSE_DEPTH = 10;
    private static final String GENERIC_ERROR_MESSAGE = "An unexpected error occurred";
    private static final String MALFORMED_BODY_MESSAGE = "Request body is malformed or unreadable";
    private static final String VALIDATION_FAILED_MESSAGE = "Request validation failed";
    private static final String CONFLICT_MESSAGE = "The request conflicts with existing data";

    /**
     * Unique constraints owned by this service, mapped to the message that is
     * safe to return. The constraint names are used for server side matching
     * only and are never sent to the caller.
     */
    private static final Map<String, String> KNOWN_UNIQUE_CONSTRAINTS = Map.of(
            "uk_applications_candidate_job", "The candidate has already applied to this job");

    // ------------------------------------------------------------------
    // Domain exceptions thrown by services
    // ------------------------------------------------------------------

    /**
     * Maps a missing resource onto {@code 404 Not Found}.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(ResourceNotFoundException ex,
            HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), request, null);
    }

    /**
     * Maps a duplicate resource onto {@code 409 Conflict}.
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateResource(DuplicateResourceException ex,
            HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_RESOURCE, ex.getMessage(), request, null);
    }

    /**
     * Maps a semantically invalid request onto {@code 400 Bad Request}.
     */
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(InvalidRequestException ex,
            HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, ex.getMessage(), request, null);
    }

    // ------------------------------------------------------------------
    // Request validation and malformed input
    // ------------------------------------------------------------------

    /**
     * Maps bean validation failures onto {@code 400 Bad Request} including
     * field level messages.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        for (ObjectError error : ex.getBindingResult().getGlobalErrors()) {
            fieldErrors.putIfAbsent(error.getObjectName(), error.getDefaultMessage());
        }
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, VALIDATION_FAILED_MESSAGE,
                request, fieldErrors);
    }

    /**
     * Maps constraint violations raised by method level validation onto
     * {@code 400 Bad Request} including field level messages.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
            HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String property = violation.getPropertyPath().toString();
            String field = property.substring(property.lastIndexOf('.') + 1);
            fieldErrors.putIfAbsent(field, violation.getMessage());
        }
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, VALIDATION_FAILED_MESSAGE,
                request, fieldErrors);
    }

    /**
     * Maps a path, query or matrix variable that cannot be converted onto
     * {@code 400 Bad Request}, for example a malformed UUID or an unknown
     * status value.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        Class<?> requiredType = ex.getRequiredType();
        ErrorCode code = codeForType(requiredType);
        String message;
        if (requiredType == UUID.class) {
            message = "Malformed UUID '" + ex.getValue() + "' for parameter '" + ex.getName() + "'";
        } else if (requiredType != null && requiredType.isEnum()) {
            message = "Unknown " + requiredType.getSimpleName() + " value '" + ex.getValue()
                    + "' for parameter '" + ex.getName() + "'";
        } else {
            message = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'";
        }
        return respond(HttpStatus.BAD_REQUEST, code, message, request, null);
    }

    /**
     * Maps any other type mismatch raised during argument binding onto
     * {@code 400 Bad Request}.
     */
    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleBeanTypeMismatch(TypeMismatchException ex,
            HttpServletRequest request) {
        String message = "Invalid value '" + ex.getValue() + "' for parameter '"
                + (ex.getPropertyName() != null ? ex.getPropertyName() : "unknown") + "'";
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, message, request, null);
    }

    /**
     * Maps an unreadable request body onto {@code 400 Bad Request}. Unknown
     * enum values, such as an invalid status, keep their own error code.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        if (ex.getCause() instanceof InvalidFormatException invalidFormat
                && invalidFormat.getTargetType() != null
                && invalidFormat.getTargetType().isEnum()) {
            Class<?> enumType = invalidFormat.getTargetType();
            String message = "Unknown " + enumType.getSimpleName() + " value '" + invalidFormat.getValue() + "'";
            return respond(HttpStatus.BAD_REQUEST, codeForType(enumType), message, request, null);
        }
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, MALFORMED_BODY_MESSAGE, request, null);
    }

    /**
     * Maps a missing required request parameter onto {@code 400 Bad Request}.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
            HttpServletRequest request) {
        String message = "Missing required parameter '" + ex.getParameterName() + "'";
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, message, request, null);
    }

    /**
     * Maps a missing or malformed request header onto {@code 400 Bad Request}.
     */
    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiErrorResponse> handleServletRequestBinding(ServletRequestBindingException ex,
            HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, ex.getMessage(), request, null);
    }

    // ------------------------------------------------------------------
    // Framework level HTTP errors that must keep their status
    // ------------------------------------------------------------------

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException ex,
            HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "Resource not found", request, null);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoHandlerFound(NoHandlerFoundException ex,
            HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "Resource not found", request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        String message = "Method '" + ex.getMethod() + "' is not supported for this request";
        return respond(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.INVALID_REQUEST, message, request, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {
        String message = "Unsupported media type '" + ex.getContentType() + "'";
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.INVALID_REQUEST, message, request, null);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex,
            HttpServletRequest request) {
        return respond(HttpStatus.NOT_ACCEPTABLE, ErrorCode.INVALID_REQUEST,
                "Requested media type is not acceptable", request, null);
    }

    /**
     * Maps framework exceptions that already carry an HTTP status, such as
     * {@code ResponseStatusException}, without leaking their internals.
     */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiErrorResponse> handleErrorResponse(ErrorResponseException ex,
            HttpServletRequest request) {
        HttpStatus status = ex.getStatusCode() instanceof HttpStatus httpStatus
                ? httpStatus
                : HttpStatus.INTERNAL_SERVER_ERROR;
        String detail = ex.getBody().getDetail();
        String message = detail != null ? detail : status.getReasonPhrase();
        return respond(status, codeForStatus(status), message, request, null);
    }

    // ------------------------------------------------------------------
    // Persistence conflicts
    // ------------------------------------------------------------------

    /**
     * Maps a database constraint violation onto {@code 409 Conflict}.
     *
     * <p>Known unique constraints, such as a duplicate email, produce a
     * specific message; every other integrity conflict produces a generic one.
     * Neither the SQL nor any database detail leaves the server.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex,
            HttpServletRequest request) {
        log.warn("Database conflict of type {} while processing {} {}",
                ex.getClass().getSimpleName(), request.getMethod(), request.getRequestURI());
        String diagnostics = diagnostics(ex);
        for (Map.Entry<String, String> known : KNOWN_UNIQUE_CONSTRAINTS.entrySet()) {
            if (diagnostics.contains(known.getKey())) {
                return respond(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_RESOURCE, known.getValue(), request, null);
            }
        }
        return respond(HttpStatus.CONFLICT, ErrorCode.CONFLICT, CONFLICT_MESSAGE, request, null);
    }

    // ------------------------------------------------------------------
    // Anything else
    // ------------------------------------------------------------------

    /**
     * Last resort: logs the full exception server side and answers with a
     * generic {@code 500 Internal Server Error} that exposes nothing about the
     * failure.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception while processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, GENERIC_ERROR_MESSAGE,
                request, null);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private ResponseEntity<ApiErrorResponse> respond(HttpStatus status, ErrorCode code, String message,
            HttpServletRequest request, Map<String, String> fieldErrors) {
        ApiErrorResponse body = ApiErrorResponse.of(
                status, code, message, request.getRequestURI(), requestId(request), fieldErrors);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private String requestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        return requestId.length() > MAX_REQUEST_ID_LENGTH
                ? requestId.substring(0, MAX_REQUEST_ID_LENGTH)
                : requestId;
    }

    /**
     * Collects the messages of the exception chain so that known constraint
     * names can be recognised. The result is used for matching only and is
     * never returned to the caller.
     */
    private String diagnostics(Throwable ex) {
        StringBuilder diagnostics = new StringBuilder();
        Throwable current = ex;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current.getMessage() != null) {
                diagnostics.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return diagnostics.toString();
    }

    private ErrorCode codeForType(Class<?> type) {
        return type != null && type.isEnum() && type.getSimpleName().endsWith("Status")
                ? ErrorCode.INVALID_STATUS
                : ErrorCode.INVALID_REQUEST;
    }

    private ErrorCode codeForStatus(HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return ErrorCode.RESOURCE_NOT_FOUND;
        }
        if (status.is4xxClientError()) {
            return ErrorCode.INVALID_REQUEST;
        }
        return ErrorCode.INTERNAL_ERROR;
    }
}
