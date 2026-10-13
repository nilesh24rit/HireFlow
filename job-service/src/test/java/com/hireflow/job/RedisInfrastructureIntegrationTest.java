package com.hireflow.job;

import com.hireflow.job.security.TestSigningKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RedisInfrastructureIntegrationTest {

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
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void redisConnectionAndJsonRoundTripWork() {
        String key = "job:listing:" + UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", UUID.randomUUID().toString());
        payload.put("status", "OPEN");
        payload.put("location", "Remote");

        redisTemplate.opsForValue().set(key, payload);

        Object actual = redisTemplate.opsForValue().get(key);
        assertThat(actual).isInstanceOf(Map.class);
        assertThat((Map<?, ?>) actual).containsEntry("status", "OPEN");
    }

    @Test
    void malformedSerializedValueFailsCleanly() {
        String key = "job:malformed:" + UUID.randomUUID();
        redisTemplate.getConnectionFactory().getConnection().set(
                key.getBytes(StandardCharsets.UTF_8),
                "{".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> redisTemplate.opsForValue().get(key))
                .isInstanceOfAny(SerializationException.class, IllegalArgumentException.class);
    }

    @Test
    void actuatorReportsRedisHealth() {
        String response = restTemplate.getForObject("/actuator/health", String.class);
        assertThat(response).contains("UP");
    }
}
