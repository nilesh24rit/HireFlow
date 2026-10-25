CREATE TABLE analytics_application_projection (
    application_id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL,
    job_id UUID NOT NULL,
    current_status VARCHAR(64) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    last_status_changed_at TIMESTAMPTZ NOT NULL,
    withdrawn_at TIMESTAMPTZ,
    last_event_id UUID NOT NULL,
    last_event_sequence BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_analytics_application_job ON analytics_application_projection (job_id);
CREATE INDEX idx_analytics_application_status ON analytics_application_projection (current_status);
CREATE INDEX idx_analytics_application_submitted ON analytics_application_projection (submitted_at);
CREATE INDEX idx_analytics_application_event ON analytics_application_projection (last_event_id);
