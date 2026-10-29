package com.hireflow.notification.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.hireflow.notification.domain.DeliveryStatus;
import com.hireflow.notification.domain.Notification;
import com.hireflow.notification.domain.NotificationType;

@Repository
public class NotificationRepository {

    private static final RowMapper<Notification> ROW_MAPPER = NotificationRepository::mapRow;
    private final JdbcTemplate jdbcTemplate;

    public NotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Notification> findByEventRecipientAndType(UUID eventId, UUID recipientUserId,
            NotificationType type) {
        List<Notification> results = jdbcTemplate.query("""
                select id, event_id, recipient_user_id, notification_type, subject, message, channel,
                       delivery_status, attempt_count, created_at, sent_at, next_attempt_at, last_error, is_read
                  from notifications
                 where event_id = ? and recipient_user_id = ? and notification_type = ?
                """, ROW_MAPPER, eventId, recipientUserId, type.name());
        return results.stream().findFirst();
    }

    public Notification save(Notification notification) {
        jdbcTemplate.update("""
                insert into notifications
                    (id, event_id, recipient_user_id, notification_type, subject, message, channel,
                     delivery_status, attempt_count, created_at, sent_at, next_attempt_at, last_error, is_read)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (event_id, recipient_user_id, notification_type) do nothing
                """, notification.id(), notification.eventId(), notification.recipientUserId(),
                notification.notificationType().name(), notification.subject(), notification.message(),
                notification.channel(), notification.deliveryStatus().name(), notification.attemptCount(),
                notification.createdAt(), notification.sentAt(), notification.nextAttemptAt(),
                notification.lastError(), notification.read());
        return notification;
    }

    public List<Notification> findForRecipient(UUID recipientUserId, int limit, int offset) {
        return jdbcTemplate.query("""
                select id, event_id, recipient_user_id, notification_type, subject, message, channel,
                       delivery_status, attempt_count, created_at, sent_at, next_attempt_at, last_error, is_read
                  from notifications
                 where recipient_user_id = ?
                 order by created_at desc
                 limit ? offset ?
                """, ROW_MAPPER, recipientUserId, limit, offset);
    }

    public Optional<Notification> findByIdForRecipient(UUID id, UUID recipientUserId) {
        List<Notification> results = jdbcTemplate.query("""
                select id, event_id, recipient_user_id, notification_type, subject, message, channel,
                       delivery_status, attempt_count, created_at, sent_at, next_attempt_at, last_error, is_read
                  from notifications where id = ? and recipient_user_id = ?
                """, ROW_MAPPER, id, recipientUserId);
        return results.stream().findFirst();
    }

    public int markRead(UUID id, UUID recipientUserId) {
        return jdbcTemplate.update(
                "update notifications set is_read = true where id = ? and recipient_user_id = ?",
                id, recipientUserId);
    }

    public List<Notification> findPending(int limit) {
        return jdbcTemplate.query("""
                select id, event_id, recipient_user_id, notification_type, subject, message, channel,
                       delivery_status, attempt_count, created_at, sent_at, next_attempt_at, last_error, is_read
                  from notifications
                 where delivery_status in ('PENDING', 'FAILED')
                   and (next_attempt_at is null or next_attempt_at <= current_timestamp)
                 order by created_at
                 limit ?
                """, ROW_MAPPER, limit);
    }

    public int claim(UUID id) {
        return jdbcTemplate.update("""
                update notifications
                   set delivery_status = 'PROCESSING', attempt_count = attempt_count + 1
                 where id = ? and delivery_status in ('PENDING', 'FAILED')
                   and (next_attempt_at is null or next_attempt_at <= current_timestamp)
                """, id);
    }

    public int markSent(UUID id) {
        return jdbcTemplate.update("""
                update notifications set delivery_status = 'SENT', sent_at = current_timestamp,
                    next_attempt_at = null, last_error = null where id = ?
                """, id);
    }

    public int markFailed(UUID id, String error, Instant nextAttemptAt, boolean permanent) {
        return jdbcTemplate.update("""
                update notifications set delivery_status = ?, last_error = ?, next_attempt_at = ?
                 where id = ?
                """, permanent ? DeliveryStatus.ABANDONED.name() : DeliveryStatus.FAILED.name(),
                error, nextAttemptAt, id);
    }

    public int recoverAbandoned(Instant cutoff) {
        return jdbcTemplate.update("""
                update notifications set delivery_status = 'FAILED', next_attempt_at = current_timestamp,
                    last_error = 'Delivery worker abandoned processing'
                 where delivery_status = 'PROCESSING' and created_at < ?
                """, cutoff);
    }

    private static Notification mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Notification(
                rs.getObject("id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getObject("recipient_user_id", UUID.class),
                NotificationType.valueOf(rs.getString("notification_type")),
                rs.getString("subject"),
                rs.getString("message"),
                rs.getString("channel"),
                DeliveryStatus.valueOf(rs.getString("delivery_status")),
                rs.getInt("attempt_count"),
                rs.getTimestamp("created_at").toInstant(),
                toInstant(rs, "sent_at"),
                toInstant(rs, "next_attempt_at"),
                rs.getString("last_error"),
                rs.getBoolean("is_read"));
    }

    private static Instant toInstant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
