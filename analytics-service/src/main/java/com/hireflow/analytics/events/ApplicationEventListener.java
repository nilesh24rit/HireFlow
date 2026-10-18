package com.hireflow.analytics.events;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.hireflow.contracts.events.ApplicationEvent;

@Component
public class ApplicationEventListener {

    private final ApplicationEventHandler handler;

    public ApplicationEventListener(ApplicationEventHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(topics = "${hireflow.kafka.applications-topic:hireflow.events.applications.v1}")
    public void onApplicationEvent(ApplicationEvent event) {
        handler.handle(event);
    }
}
