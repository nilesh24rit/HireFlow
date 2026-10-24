CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    recipient_user_id UUID NOT NULL,
    notification_type VARCHAR(64) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    channel VARCHAR(32) NOT NULL,
    delivery_status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_notification_event_recipient_type
        UNIQUE (event_id, recipient_user_id, notification_type)
);

CREATE INDEX ix_notifications_recipient_created
    ON notifications (recipient_user_id, created_at DESC);
CREATE INDEX ix_notifications_delivery
    ON notifications (delivery_status, next_attempt_at);
