package com.hireflow.auth.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.hireflow.auth.error.ApiErrorResponse;
import com.hireflow.auth.error.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes every response produced outside a controller during Google sign-in — the success
 * handler's handoff, and both handlers' failures — in the common HireFlow conventions.
 *
 * <p>Two deliberate properties apply to everything written here:</p>
 *
 * <ul>
 *   <li><b>One error contract.</b> {@link #writeError} renders the same
 *       {@code {timestamp, status, error, code, message, path}} JSON as
 *       {@link GlobalExceptionHandler} and {@link SecurityErrorResponseHandler}, so an
 *       OAuth2 refusal is not a second, alien error shape. No stack trace, claim value,
 *       provider token or internal detail is ever written; the message is always one this
 *       service chose.</li>
 *   <li><b>Never cacheable.</b> Responses carry {@code Cache-Control: no-store}. A handoff
 *       code in particular must not be stored by any intermediary, since possessing it —
 *       briefly — means possessing the token it guards.</li>
 * </ul>
 *
 * <p>Responses are written directly rather than through {@code sendError}: a
 * {@code sendError} triggers the container's error dispatch to the protected
 * {@code /error} path, which would rewrite the status and path this class deliberately
 * chose.</p>
 */
@Component
public class OAuth2ResponseWriter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** Mirrors {@link SecurityErrorResponseHandler} so both layers echo correlation ids alike. */
    private static final int MAX_REQUEST_ID_LENGTH = 64;

    private final ObjectMapper objectMapper;

    public OAuth2ResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Writes a Google sign-in failure in the common HireFlow error contract.
     *
     * @param request   the callback request, used for its path and correlation id
     * @param response  response to write
     * @param status    HTTP status of the refusal
     * @param code      application error code
     * @param message   safe, human readable message this service chose to expose
     * @throws IOException when the response body cannot be written
     */
    public void writeError(HttpServletRequest request, HttpServletResponse response,
            HttpStatus status, ErrorCode code, String message) throws IOException {
        ApiErrorResponse body = ApiErrorResponse.of(status, code, message,
                request.getRequestURI(), requestId(request), null);
        writeJson(response, status, body);
    }

    /**
     * Writes an arbitrary JSON body at the given status.
     *
     * @param response response to write
     * @param status   HTTP status of the response
     * @param body     value to serialize as JSON
     * @throws IOException when the response body cannot be written
     */
    public void writeJson(HttpServletResponse response, HttpStatus status, Object body)
            throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Authentication responses are never cacheable: a stored copy could replay a
        // one-time handoff or preserve the outcome of a sign-in attempt.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        objectMapper.writeValue(response.getWriter(), body);
    }

    /** Truncated {@code X-Request-Id} echo, mirroring the other response writers. */
    private String requestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        return requestId.length() > MAX_REQUEST_ID_LENGTH
                ? requestId.substring(0, MAX_REQUEST_ID_LENGTH)
                : requestId;
    }
}
