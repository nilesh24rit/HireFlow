package com.hireflow.gateway.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Development target URIs for the internal HireFlow services behind the gateway.
 *
 * <p>Defaults point at the local development ports of each service. Every URI can be
 * overridden through configuration or environment variables, so no environment-specific
 * value is fixed in code. The values are plain service addresses — they carry no
 * credentials or secrets, and service discovery can replace them later without changing
 * the route definitions.</p>
 */
@ConfigurationProperties(prefix = "hireflow.services")
public record HireFlowServiceUris(
        URI authServiceUri,
        URI candidateServiceUri,
        URI jobServiceUri,
        URI applicationServiceUri) {

    public HireFlowServiceUris {
        authServiceUri = requireHttp(authServiceUri, "http://localhost:8081");
        candidateServiceUri = requireHttp(candidateServiceUri, "http://localhost:8082");
        jobServiceUri = requireHttp(jobServiceUri, "http://localhost:8083");
        applicationServiceUri = requireHttp(applicationServiceUri, "http://localhost:8084");
    }

    private static URI requireHttp(URI uri, String fallback) {
        URI resolved = uri != null ? uri : URI.create(fallback);
        String scheme = resolved.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException(
                    "Service URI must use http or https but was: " + scheme);
        }
        return resolved;
    }
}
