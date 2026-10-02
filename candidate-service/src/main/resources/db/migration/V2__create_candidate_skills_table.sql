CREATE TABLE candidate_skills (
    id UUID NOT NULL,
    candidate_id UUID NOT NULL,
    skill VARCHAR(100) NOT NULL,
    CONSTRAINT pk_candidate_skills PRIMARY KEY (id),
    CONSTRAINT fk_candidate_skills_candidate FOREIGN KEY (candidate_id)
        REFERENCES candidates (id) ON DELETE CASCADE,
    CONSTRAINT chk_candidate_skills_skill_not_blank CHECK (btrim(skill) <> '')
);

CREATE UNIQUE INDEX uk_candidate_skills_candidate_skill
    ON candidate_skills (candidate_id, lower(skill));
