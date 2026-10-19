CREATE TABLE application_outbox (
    id UUID NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    event_version INTEGER NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error TEXT,
    CONSTRAINT pk_application_outbox PRIMARY KEY (id),
    CONSTRAINT chk_application_outbox_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PUBLISHED'))
);

CREATE INDEX idx_application_outbox_delivery
    ON application_outbox (status, next_attempt_at, created_at);

CREATE INDEX idx_application_outbox_aggregate
    ON application_outbox (aggregate_id, created_at, id);
