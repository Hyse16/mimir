ALTER TABLE ai_jobs ADD COLUMN previous_turn_id UUID;

ALTER TABLE ai_jobs
    ADD CONSTRAINT ai_jobs_previous_turn_fk
        FOREIGN KEY (previous_turn_id) REFERENCES ai_jobs (id),
    ADD CONSTRAINT ai_jobs_previous_turn_check CHECK (
        (job_type = 'IMAGE_ANALYSIS' AND previous_turn_id IS NULL)
        OR
        (job_type = 'BLOG_DRAFT_GENERATION'
            AND (previous_turn_id IS NULL OR previous_turn_id <> id))
    );

CREATE INDEX ai_jobs_previous_turn_idx
    ON ai_jobs (previous_turn_id)
    WHERE previous_turn_id IS NOT NULL;
