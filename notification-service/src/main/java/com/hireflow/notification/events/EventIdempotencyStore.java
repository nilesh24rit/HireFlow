package com.hireflow.notification.events;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventIdempotencyStore {

    private final JdbcTemplate jdbcTemplate;

    public EventIdempotencyStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean recordIfNew(UUID eventId) {
        return jdbcTemplate.update(
                "insert into notification_processed_events (event_id) values (?) on conflict do nothing",
                eventId) == 1;
    }
}
