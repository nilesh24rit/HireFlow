CREATE TABLE candidates (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    headline VARCHAR(200),
    summary TEXT,
    location VARCHAR(200),
    years_of_experience INTEGER,
    current_company VARCHAR(200),
    current_job_title VARCHAR(200),
    resume_url VARCHAR(500),
    linkedin_url VARCHAR(500),
    github_url VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_candidates PRIMARY KEY (id),
    CONSTRAINT uk_candidates_user_id UNIQUE (user_id)
);
