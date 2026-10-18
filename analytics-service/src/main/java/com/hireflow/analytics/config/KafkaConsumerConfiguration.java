package com.hireflow.analytics.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import com.hireflow.contracts.events.ApplicationEvent;

@Configuration
public class KafkaConsumerConfiguration {

    @Bean
    ConsumerFactory<String, Object> kafkaConsumerFactory(
            KafkaProperties bootProperties, KafkaInfrastructureProperties properties) {
        Map<String, Object> consumerProperties = new HashMap<>(
                bootProperties.buildConsumerProperties());
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class);
        consumerProperties.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS,
                StringDeserializer.class);
        consumerProperties.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS,
                JsonDeserializer.class);
        consumerProperties.put(JsonDeserializer.TRUSTED_PACKAGES, "java.util,com.hireflow");
        consumerProperties.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        consumerProperties.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
                ApplicationEvent.class.getName());
        consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, properties.getConsumerGroup());
        consumerProperties.put(ConsumerConfig.CLIENT_ID_CONFIG, properties.getConsumerClientId());
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProperties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, properties.getMaxPollRecords());
        consumerProperties.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG,
                properties.getMaxPollIntervalMs());
        return new DefaultKafkaConsumerFactory<>(consumerProperties);
    }

    @Bean
    CommonErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1000L, 3L));
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            CommonErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
