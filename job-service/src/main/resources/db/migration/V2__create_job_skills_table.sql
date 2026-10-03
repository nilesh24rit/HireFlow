CREATE TABLE job_skills (
    id UUID NOT NULL,
    job_id UUID NOT NULL,
    skill VARCHAR(100) NOT NULL,
    CONSTRAINT pk_job_skills PRIMARY KEY (id),
    CONSTRAINT fk_job_skills_job FOREIGN KEY (job_id)
        REFERENCES jobs (id) ON DELETE CASCADE,
    CONSTRAINT chk_job_skills_skill_not_blank CHECK (btrim(skill) <> '')
);

CREATE UNIQUE INDEX uk_job_skills_job_skill
    ON job_skills (job_id, lower(skill));
