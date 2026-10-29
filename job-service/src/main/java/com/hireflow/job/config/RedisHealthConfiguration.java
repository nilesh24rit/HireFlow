package com.hireflow.job.config;

import org.springframework.boot.data.redis.health.DataRedisHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
public class RedisHealthConfiguration {

    @Bean
    public DataRedisHealthIndicator redisHealthIndicator(RedisConnectionFactory redisConnectionFactory) {
        return new DataRedisHealthIndicator(redisConnectionFactory);
    }
}
