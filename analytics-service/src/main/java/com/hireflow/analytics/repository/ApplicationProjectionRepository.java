package com.hireflow.analytics.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.hireflow.contracts.events.ApplicationEvent;

@Repository
public class ApplicationProjectionRepository {

    private final JdbcTemplate jdbcTemplate;

    public ApplicationProjectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Projection> findByApplicationId(UUID applicationId) {
        return jdbcTemplate.query("""
                select application_id, candidate_id, job_id, current_status, submitted_at,
                       last_status_changed_at, withdrawn_at, last_event_id, last_event_sequence
                  from analytics_application_projection
                 where application_id = ?
                """, this::map, applicationId).stream().findFirst();
    }

    public void insert(ApplicationEvent event, long sequence) {
        var p = event.payload();
        jdbcTemplate.update("""
                insert into analytics_application_projection
                    (application_id, candidate_id, job_id, current_status, submitted_at,
                     last_status_changed_at, withdrawn_at, last_event_id, last_event_sequence)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, p.applicationId(), p.candidateId(), p.jobId(), p.currentStatus(),
                event.occurredAt(), event.occurredAt(),
                "WITHDRAWN".equals(p.currentStatus()) ? event.occurredAt() : null,
                event.eventId(), sequence);
    }

    public void update(ApplicationEvent event, long sequence) {
        var p = event.payload();
        jdbcTemplate.update("""
                update analytics_application_projection
                   set current_status = ?, last_status_changed_at = ?, withdrawn_at = ?,
                       last_event_id = ?, last_event_sequence = ?, updated_at = current_timestamp
                 where application_id = ?
                """, p.currentStatus(), event.occurredAt(),
                "WITHDRAWN".equals(p.currentStatus()) ? event.occurredAt() : null,
                event.eventId(), sequence, p.applicationId());
    }

    private Projection map(ResultSet rs, int row) throws SQLException {
        return new Projection(rs.getObject("application_id", UUID.class),
                rs.getObject("candidate_id", UUID.class), rs.getObject("job_id", UUID.class),
                rs.getString("current_status"),
                rs.getObject("submitted_at", java.time.OffsetDateTime.class).toInstant(),
                rs.getObject("last_status_changed_at", java.time.OffsetDateTime.class).toInstant(),
                rs.getObject("withdrawn_at", java.time.OffsetDateTime.class) == null ? null
                        : rs.getObject("withdrawn_at", java.time.OffsetDateTime.class).toInstant(),
                rs.getObject("last_event_id", UUID.class),
                rs.getLong("last_event_sequence"));
    }

    public record Projection(UUID applicationId, UUID candidateId, UUID jobId, String currentStatus,
            Instant submittedAt, Instant lastStatusChangedAt, Instant withdrawnAt,
            UUID lastEventId, long lastEventSequence) {
    }
}
