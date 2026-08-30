ALTER TABLE ai_jobs ADD COLUMN generation_target VARCHAR(16);

UPDATE ai_jobs
SET generation_target = 'FULL'
WHERE job_type = 'BLOG_DRAFT_GENERATION';

ALTER TABLE ai_jobs
    ADD CONSTRAINT ai_jobs_generation_target_check CHECK (
        (job_type = 'IMAGE_ANALYSIS' AND generation_target IS NULL)
        OR
        (job_type = 'BLOG_DRAFT_GENERATION'
            AND generation_target IS NOT NULL
            AND generation_target IN ('FULL', 'TITLE', 'BODY', 'TAGS'))
    );
