package com.hireflow.candidate;

import com.hireflow.candidate.security.TestSigningKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RedisHealthCheckTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("hireflow.redis.host", redis::getHost);
        registry.add("hireflow.redis.port", () -> redis.getMappedPort(6379));
        registry.add("hireflow.redis.database", () -> 0);
        registry.add("hireflow.redis.timeout", () -> "2s");
        registry.add("hireflow.redis.ssl", () -> false);
        registry.add("management.endpoints.web.exposure.include", () -> "health,info");
        registry.add("hireflow.jwt.signing-key", () -> TestSigningKeys.VALID);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointReportsUp() {
        String response = restTemplate.getForObject("/actuator/health", String.class);
        assertThat(response).contains("UP");
    }
}
