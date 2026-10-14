package com.hireflow.candidate.cache;

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
import com.hireflow.candidate.config.CachePolicyProperties;
import com.hireflow.candidate.dto.CandidateResponse;

@Component
public class CandidateCache {

    private static final Logger log = LoggerFactory.getLogger(CandidateCache.class);
    private static final String PROFILE_SEGMENT = "profile:";
    private static final String USER_SEGMENT = "user:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CachePolicyProperties properties;

    public CandidateCache(RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper, CachePolicyProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    private CandidateCache() {
        this.redisTemplate = null;
        this.objectMapper = null;
        this.properties = null;
    }

    public static CandidateCache disabled() {
        return new CandidateCache();
    }

    public CandidateResponse getById(UUID id) {
        return get(key(PROFILE_SEGMENT, id), CandidateResponse.class);
    }

    public CandidateResponse getByUserId(UUID userId) {
        return get(key(USER_SEGMENT, userId), CandidateResponse.class);
    }

    public void cacheAfterCommit(CandidateResponse response) {
        afterCommit(() -> {
            put(key(PROFILE_SEGMENT, response.id()), response, properties.getCandidateProfileTtl());
            put(key(USER_SEGMENT, response.userId()), response, properties.getCandidateUserTtl());
        });
    }

    public void evictAfterCommit(UUID candidateId, UUID userId) {
        afterCommit(() -> {
            evict(key(PROFILE_SEGMENT, candidateId));
            if (userId != null) {
                evict(key(USER_SEGMENT, userId));
            }
        });
    }

    private <T> T get(String key, Class<T> type) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            return type.isInstance(value) ? type.cast(value) : objectMapper.convertValue(value, type);
        } catch (DataAccessException | SerializationException | IllegalArgumentException ex) {
            log.warn("Candidate cache read failed for {}: {}", key, ex.getClass().getSimpleName());
            return null;
        }
    }

    private void put(String key, CandidateResponse response, Duration ttl) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, response, ttl);
        } catch (DataAccessException | SerializationException ex) {
            log.warn("Candidate cache write failed for {}: {}", key, ex.getClass().getSimpleName());
        }
    }

    private void evict(String key) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException ex) {
            log.warn("Candidate cache eviction failed for {}: {}", key, ex.getClass().getSimpleName());
        }
    }

    private String key(String segment, UUID id) {
        return properties == null ? "" : properties.getKeyPrefix() + segment + id;
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
