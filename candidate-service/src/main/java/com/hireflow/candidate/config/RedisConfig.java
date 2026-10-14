package com.hireflow.candidate.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

@Configuration
@EnableCaching
@EnableConfigurationProperties({RedisProperties.class, CachePolicyProperties.class})
public class RedisConfig {

    @Bean
    @ConditionalOnMissingBean(RedisConnectionFactory.class)
    public RedisConnectionFactory redisConnectionFactory(
            com.hireflow.candidate.config.RedisProperties properties) {
        RedisStandaloneConfiguration redisConfiguration = new RedisStandaloneConfiguration();
        redisConfiguration.setHostName(properties.getHost());
        redisConfiguration.setPort(properties.getPort());
        redisConfiguration.setDatabase(properties.getDatabase());
        if (StringUtils.hasText(properties.getUsername())) {
            redisConfiguration.setUsername(properties.getUsername());
        }
        if (StringUtils.hasText(properties.getPassword())) {
            redisConfiguration.setPassword(RedisPassword.of(properties.getPassword()));
        }

        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientBuilder =
            LettuceClientConfiguration.builder().commandTimeout(properties.getTimeout());
        if (properties.isSsl()) {
            clientBuilder.useSsl();
        }
        LettuceClientConfiguration clientConfiguration = clientBuilder.build();

        return new LettuceConnectionFactory(redisConfiguration, clientConfiguration);
    }

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Jackson2JsonRedisSerializer<Object> jsonSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        jsonSerializer.setObjectMapper(objectMapper);

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
