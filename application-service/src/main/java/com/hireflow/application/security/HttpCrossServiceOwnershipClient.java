package com.hireflow.application.security;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * HTTP-based implementation of {@link CrossServiceOwnershipClient}.
 *
 * <p>Queries candidate-service and job-service endpoints forwarding the authenticated
 * bearer token. Fails closed (returns {@code false}) on any error, timeout, or 404.</p>
 */
@Component
public class HttpCrossServiceOwnershipClient implements CrossServiceOwnershipClient {

    private static final Logger log = LoggerFactory.getLogger(HttpCrossServiceOwnershipClient.class);

    private final RestClient restClient;
    private final String candidateServiceUrl;
    private final String jobServiceUrl;

    public HttpCrossServiceOwnershipClient(
            RestClient.Builder restClientBuilder,
            @Value("${hireflow.services.candidate-service.url:http://localhost:8082}") String candidateServiceUrl,
            @Value("${hireflow.services.job-service.url:http://localhost:8083}") String jobServiceUrl) {
        this.restClient = restClientBuilder.build();
        this.candidateServiceUrl = candidateServiceUrl;
        this.jobServiceUrl = jobServiceUrl;
    }

    @Override
    public boolean isCandidateOwner(UUID candidateId, UUID userId) {
        if (candidateId == null || userId == null) {
            return false;
        }
        try {
            String token = extractBearerToken();
            var spec = restClient.get()
                    .uri(candidateServiceUrl + "/api/candidates/{id}", candidateId);
            if (token != null) {
                spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            CandidateDto candidate = spec.retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        log.debug("Candidate lookup failed with status: {}", resp.getStatusCode());
                    })
                    .body(CandidateDto.class);
            return candidate != null && userId.equals(candidate.userId());
        } catch (Exception ex) {
            log.warn("Failed to verify candidate ownership for candidateId {}: {}", candidateId, ex.getMessage());
            return false;
        }
    }

    @Override
    public boolean isJobOwner(UUID jobId, UUID recruiterId) {
        if (jobId == null || recruiterId == null) {
            return false;
        }
        try {
            String token = extractBearerToken();
            var spec = restClient.get()
                    .uri(jobServiceUrl + "/api/jobs/{id}", jobId);
            if (token != null) {
                spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            JobDto job = spec.retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        log.debug("Job lookup failed with status: {}", resp.getStatusCode());
                    })
                    .body(JobDto.class);
            return job != null && recruiterId.equals(job.recruiterId());
        } catch (Exception ex) {
            log.warn("Failed to verify job ownership for jobId {}: {}", jobId, ex.getMessage());
            return false;
        }
    }

    private String extractBearerToken() {
        RequestAttributes attribs = RequestContextHolder.getRequestAttributes();
        if (attribs instanceof ServletRequestAttributes servletAttribs) {
            HttpServletRequest request = servletAttribs.getRequest();
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.toLowerCase().startsWith("bearer ")) {
                return header.substring(7).trim();
            }
        }
        return null;
    }

    private record CandidateDto(UUID id, UUID userId) {}
    private record JobDto(UUID id, UUID recruiterId) {}
}
