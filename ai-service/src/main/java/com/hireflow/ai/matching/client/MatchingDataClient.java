package com.hireflow.ai.matching.client;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hireflow.ai.config.MatchingProperties;
import com.hireflow.ai.matching.api.CandidateMatchProfile;
import com.hireflow.ai.matching.api.JobMatchProfile;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class MatchingDataClient {

    private final RestClient restClient;
    private final MatchingProperties properties;
    private final HttpServletRequest request;

    public MatchingDataClient(RestClient restClient, MatchingProperties properties, HttpServletRequest request) {
        this.restClient = restClient;
        this.properties = properties;
        this.request = request;
    }

    public CandidateMatchProfile getCandidateByUserId(UUID userId) {
        CandidateDto candidate = get(properties.candidateServiceUrl() + "/api/candidates/user/{id}",
                CandidateDto.class, userId);
        return candidateProfile(candidate);
    }

    public CandidateMatchProfile getCandidateById(UUID candidateId) {
        CandidateDto candidate = get(properties.candidateServiceUrl() + "/api/candidates/{id}",
                CandidateDto.class, candidateId);
        return candidateProfile(candidate);
    }

    public JobMatchProfile getJob(UUID jobId) {
        JobDto job = get(properties.jobServiceUrl() + "/api/jobs/{id}", JobDto.class, jobId);
        return new JobMatchProfile(job.id(), job.skills(), List.of(), job.experienceMin(), job.experienceMax(),
                List.of(), job.description(), job.status());
    }

    public boolean isApplicant(UUID jobId, UUID candidateId) {
        ApplicationDto[] applications = get(properties.applicationServiceUrl()
                + "/api/applications/job/{jobId}", ApplicationDto[].class, jobId);
        return applications != null && java.util.Arrays.stream(applications)
                .anyMatch(application -> candidateId.equals(application.candidateId()));
    }

    private <T> T get(String uri, Class<T> type, Object... variables) {
        try {
            var spec = restClient.get().uri(uri, variables);
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                spec.header(HttpHeaders.AUTHORIZATION, authorization);
            }
            return spec.retrieve()
                    .onStatus(HttpStatusCode::isError, (req, response) -> {
                        throw new MatchingDataUnavailableException(
                                HttpStatus.resolve(response.getStatusCode().value()) == null
                                        ? HttpStatus.BAD_GATEWAY
                                        : HttpStatus.resolve(response.getStatusCode().value()),
                                "MATCHING_DATA_REQUEST_FAILED", "Matching data request failed", null);
                    })
                    .body(type);
        } catch (MatchingDataUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new MatchingDataUnavailableException(HttpStatus.SERVICE_UNAVAILABLE,
                    "MATCHING_DATA_UNAVAILABLE", "A matching data service is unavailable", ex);
        }
    }

    private CandidateMatchProfile candidateProfile(CandidateDto candidate) {
        if (candidate == null || candidate.id() == null) {
            throw new MatchingDataUnavailableException(HttpStatus.BAD_GATEWAY,
                    "INVALID_CANDIDATE_RESPONSE", "Candidate service returned incomplete data", null);
        }
        return new CandidateMatchProfile(candidate.id(), candidate.skills(), candidate.yearsOfExperience(),
                List.of(), List.of());
    }

    private record CandidateDto(UUID id, UUID userId, Integer yearsOfExperience, List<String> skills) {
    }

    private record JobDto(UUID id, List<String> skills, Integer experienceMin, Integer experienceMax,
            String description, String status) {
    }

    private record ApplicationDto(UUID candidateId) {
    }
}
