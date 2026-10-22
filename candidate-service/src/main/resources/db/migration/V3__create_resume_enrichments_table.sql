CREATE TABLE resume_enrichments (
    id UUID NOT NULL,
    candidate_id UUID NOT NULL,
    processing_id UUID NOT NULL,
    proposed_data TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_resume_enrichments PRIMARY KEY (id),
    CONSTRAINT fk_resume_enrichments_candidate FOREIGN KEY (candidate_id)
        REFERENCES candidates (id) ON DELETE CASCADE,
    CONSTRAINT chk_resume_enrichments_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'EXPIRED'))
);

CREATE INDEX ix_resume_enrichments_candidate
    ON resume_enrichments (candidate_id, created_at);
