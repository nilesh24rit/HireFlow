package com.hireflow.job.cache;

import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireflow.job.config.CachePolicyProperties;
import com.hireflow.job.dto.JobResponse;

@Component
public class JobCache {

    private static final Logger log = LoggerFactory.getLogger(JobCache.class);
    private static final String DETAILS_SEGMENT = "details:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CachePolicyProperties properties;

    public JobCache(RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper, CachePolicyProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    private JobCache() {
        this.redisTemplate = null;
        this.objectMapper = null;
        this.properties = null;
    }

    public static JobCache disabled() {
        return new JobCache();
    }

    public JobResponse getById(UUID id) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            Object value = redisTemplate.opsForValue().get(key(id));
            if (value == null) {
                return null;
            }
            return value instanceof JobResponse response
                    ? response
                    : objectMapper.convertValue(value, JobResponse.class);
        } catch (DataAccessException | SerializationException | IllegalArgumentException ex) {
            log.warn("Job cache read failed for {}: {}", key(id), ex.getClass().getSimpleName());
            return null;
        }
    }

    public void cacheAfterCommit(JobResponse response) {
        afterCommit(() -> {
            try {
                redisTemplate.opsForValue().set(key(response.id()), response, properties.getJobDetailsTtl());
            } catch (DataAccessException | SerializationException ex) {
                log.warn("Job cache write failed for {}: {}", key(response.id()), ex.getClass().getSimpleName());
            }
        });
    }

    public void evictAfterCommit(UUID id) {
        afterCommit(() -> {
            try {
                redisTemplate.delete(key(id));
            } catch (DataAccessException ex) {
                log.warn("Job cache eviction failed for {}: {}", key(id), ex.getClass().getSimpleName());
            }
        });
    }

    private String key(UUID id) {
        return properties == null ? "" : properties.getKeyPrefix() + DETAILS_SEGMENT + id;
    }

    private void afterCommit(Runnable action) {
        if (redisTemplate == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
