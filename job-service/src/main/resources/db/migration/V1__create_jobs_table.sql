CREATE TABLE jobs (
    id UUID NOT NULL,
    recruiter_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    location VARCHAR(200),
    employment_type VARCHAR(20) NOT NULL,
    experience_min INTEGER,
    experience_max INTEGER,
    salary_min INTEGER,
    salary_max INTEGER,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_jobs PRIMARY KEY (id),
    CONSTRAINT chk_jobs_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT chk_jobs_description_not_blank CHECK (btrim(description) <> ''),
    CONSTRAINT chk_jobs_employment_type CHECK
        (employment_type IN ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP')),
    CONSTRAINT chk_jobs_status CHECK
        (status IN ('DRAFT', 'OPEN', 'CLOSED', 'ARCHIVED')),
    CONSTRAINT chk_jobs_experience_non_negative
        CHECK (experience_min >= 0 AND experience_max >= 0),
    CONSTRAINT chk_jobs_experience_range
        CHECK (experience_min IS NULL OR experience_max IS NULL OR experience_min <= experience_max),
    CONSTRAINT chk_jobs_salary_non_negative
        CHECK (salary_min >= 0 AND salary_max >= 0),
    CONSTRAINT chk_jobs_salary_range
        CHECK (salary_min IS NULL OR salary_max IS NULL OR salary_min <= salary_max)
);

CREATE INDEX idx_jobs_recruiter_id ON jobs (recruiter_id);

CREATE INDEX idx_jobs_status ON jobs (status);
