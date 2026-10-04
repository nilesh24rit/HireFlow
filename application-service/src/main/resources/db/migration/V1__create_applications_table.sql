CREATE TABLE applications (
    id UUID NOT NULL,
    candidate_id UUID NOT NULL,
    job_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    cover_letter TEXT,
    applied_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_applications PRIMARY KEY (id),
    CONSTRAINT uk_applications_candidate_job UNIQUE (candidate_id, job_id),
    CONSTRAINT chk_applications_status CHECK
        (status IN ('APPLIED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW',
                    'REJECTED', 'HIRED', 'WITHDRAWN')),
    CONSTRAINT chk_applications_cover_letter_length
        CHECK (cover_letter IS NULL OR char_length(cover_letter) <= 5000)
);

CREATE INDEX idx_applications_candidate_id ON applications (candidate_id);

CREATE INDEX idx_applications_job_id ON applications (job_id);
