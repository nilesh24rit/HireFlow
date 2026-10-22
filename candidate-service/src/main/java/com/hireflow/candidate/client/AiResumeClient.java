package com.hireflow.candidate.client;

import java.util.List;
import java.util.UUID;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireflow.candidate.dto.ResumeData;
import com.hireflow.candidate.exception.InvalidRequestException;

@Component
public class AiResumeClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AiResumeClient(RestClient aiResumeRestClient, ObjectMapper objectMapper) {
        this.restClient = aiResumeRestClient;
        this.objectMapper = objectMapper;
    }

    public ResumeData process(MultipartFile file, String authorization) {
        try {
            byte[] content = file.getBytes();
            ByteArrayResource resource = new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);
            String response = restClient.post()
                    .uri("/api/ai/resumes/process")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readValue(response, ResumeData.class);
        } catch (RestClientException ex) {
            throw new InvalidRequestException("Resume processing service is unavailable");
        } catch (Exception ex) {
            throw new InvalidRequestException("Resume processing returned invalid structured data");
        }
    }
}
