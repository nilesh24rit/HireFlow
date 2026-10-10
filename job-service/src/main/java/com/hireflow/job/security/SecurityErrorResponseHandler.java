package com.hireflow.job.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import com.hireflow.job.error.ApiErrorResponse;
import com.hireflow.job.error.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Deliberate handling of the two responses Spring Security produces before any controller
 * runs, in the common HireFlow error contract:
 * {@code timestamp, status, error, code, message, path}.
 *
 * <p>Spring Security's defaults answer with an empty body. This handler keeps the same
 * statuses — 401 for missing or rejected credentials, 403 for a denial — but renders them
 * through {@link ApiErrorResponse}, so security rejections and application errors are one
 * contract rather than two. The payload is fixed and safe: it never contains stack traces,
 * SQL, credentials, session identifiers, the {@code Authorization} header or any internal
 * security detail; the exception behind the rejection is intentionally not echoed.
 *
 * <p>The response is written directly ({@link HttpServletResponse#setStatus(int)}) instead
 * of via {@code sendError(...)}: a {@code sendError} triggers the container's error
 * dispatch to the protected {@code /error} path, which rewrites the original status.
 * Writing the response makes it final, so the status chosen here is the status the client
 * observes.
 *
 * <p>The challenge is {@code Bearer}: a missing credential yields
 * {@code WWW-Authenticate: Bearer realm="HireFlow job-service"} with the generic
 * "Authentication required" message, while a supplied token that failed validation (marked
 * by {@link JwtAuthenticationFilter}) yields {@code error="invalid_token"} and "Invalid or
 * expired authentication token" — still generic, and never echoing claims or crypto
 * detail. 403 carries no challenge: authentication succeeded, permission did not. No role
 * or permission rule lives here.
 */
@Component
public class SecurityErrorResponseHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** Mirrors {@code GlobalExceptionHandler} so both layers echo correlation ids alike. */
    private static final int MAX_REQUEST_ID_LENGTH = 64;

    private static final String BEARER_CHALLENGE = "Bearer realm=\"HireFlow job-service\"";
    private static final String BEARER_INVALID_TOKEN_CHALLENGE =
            "Bearer realm=\"HireFlow job-service\", error=\"invalid_token\"";

    private static final String AUTHENTICATION_REQUIRED = "Authentication required";
    private static final String INVALID_TOKEN = "Invalid or expired authentication token";
    private static final String ACCESS_DENIED = "Access denied";

    private final ObjectMapper objectMapper;

    public SecurityErrorResponseHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Answers 401 for a request that carries no valid credentials.
     *
     * @param request               current request
     * @param response              response to write
     * @param authenticationException why the request was not authenticated; never echoed
     * @throws IOException when the response body cannot be written
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException {
        boolean invalidToken = request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE) != null;
        write(request, response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED,
                invalidToken ? INVALID_TOKEN : AUTHENTICATION_REQUIRED,
                invalidToken ? BEARER_INVALID_TOKEN_CHALLENGE : BEARER_CHALLENGE);
    }

    /**
     * Answers 403 for an authenticated request that is not allowed.
     *
     * @param request              current request
     * @param response             response to write
     * @param accessDeniedException why access was denied; never echoed
     * @throws IOException when the response body cannot be written
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, ACCESS_DENIED, null);
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
            HttpStatus status, ErrorCode code, String message, String challenge) throws IOException {
        ApiErrorResponse body = ApiErrorResponse.of(
                status, code, message, request.getRequestURI(), requestId(request), null);

        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (challenge != null) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);
        }
        objectMapper.writeValue(response.getWriter(), body);
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
}
