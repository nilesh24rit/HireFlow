package com.hireflow.candidate.error;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Common error payload returned by every HireFlow REST endpoint on failure.
 *
 * <p>Example:
 * <pre>{@code
 * {
 *   "timestamp": "2026-10-06T12:30:00Z",
 *   "status": 404,
 *   "error": "Not Found",
 *   "code": "RESOURCE_NOT_FOUND",
 *   "message": "No user found with id '...'",
 *   "path": "/api/users/123"
 * }
 * }</pre>
 *
 * <p>The payload never carries stack traces, SQL, credentials or other
 * implementation details. Optional {@code requestId} echoes the
 * {@code X-Request-Id} correlation header when the caller supplied one, and
 * optional {@code fieldErrors} reports field level validation failures.
 *
 * @param timestamp   UTC instant when the error was produced, ISO-8601
 * @param status      HTTP status code
 * @param error       HTTP reason phrase
 * @param code        application error code
 * @param message     safe, human readable summary
 * @param path        request path, without query parameters
 * @param requestId   request correlation id, when supplied by the caller
 * @param fieldErrors field to validation message map, for validation failures
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        String timestamp,
        int status,
        String error,
        ErrorCode code,
        String message,
        String path,
        String requestId,
        Map<String, String> fieldErrors) {

    private static final DateTimeFormatter UTC_TIMESTAMP = DateTimeFormatter.ISO_INSTANT;

    /**
     * Builds an error payload for the given HTTP status.
     *
     * @param status  HTTP status of the response
     * @param code    application error code
     * @param message human readable message that is safe to expose
     * @param path    request path, without query parameters
     * @return the populated error payload with a UTC timestamp
     */
    public static ApiErrorResponse of(HttpStatus status, ErrorCode code, String message, String path) {
        return of(status, code, message, path, null, null);
    }

    /**
     * Builds an error payload carrying optional correlation and field errors.
     *
     * @param status      HTTP status of the response
     * @param code        application error code
     * @param message     human readable message that is safe to expose
     * @param path        request path, without query parameters
     * @param requestId   request correlation id, may be {@code null}
     * @param fieldErrors field level validation errors, may be {@code null}
     * @return the populated error payload with a UTC timestamp
     */
    public static ApiErrorResponse of(HttpStatus status, ErrorCode code, String message, String path,
            String requestId, Map<String, String> fieldErrors) {
        return new ApiErrorResponse(
                utcTimestamp(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                path,
                requestId,
                fieldErrors);
    }

    private static String utcTimestamp() {
        return UTC_TIMESTAMP.format(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }
}
