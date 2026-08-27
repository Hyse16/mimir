ALTER TABLE ai_jobs ADD COLUMN base_version_id UUID REFERENCES blog_draft_versions (id);
ALTER TABLE ai_jobs ADD COLUMN result_version_id UUID REFERENCES blog_draft_versions (id);
ALTER TABLE ai_jobs ADD COLUMN revision_instruction TEXT;
ALTER TABLE ai_jobs ADD COLUMN error_code VARCHAR(64);

ALTER TABLE ai_jobs DROP CONSTRAINT ai_jobs_type_check;
ALTER TABLE ai_jobs
    ADD CONSTRAINT ai_jobs_type_check CHECK (job_type IN ('IMAGE_ANALYSIS', 'BLOG_DRAFT_GENERATION'));

ALTER TABLE ai_jobs DROP CONSTRAINT ai_jobs_stage_check;
ALTER TABLE ai_jobs
    ADD CONSTRAINT ai_jobs_stage_check CHECK (
        stage IN ('QUEUED', 'IMAGE_ANALYSIS', 'CONTEXT_ASSEMBLY', 'DRAFT_GENERATION', 'COMPLETE')
    );

ALTER TABLE ai_job_events DROP CONSTRAINT ai_job_events_stage_check;
ALTER TABLE ai_job_events
    ADD CONSTRAINT ai_job_events_stage_check CHECK (
        stage IN ('QUEUED', 'IMAGE_ANALYSIS', 'CONTEXT_ASSEMBLY', 'DRAFT_GENERATION', 'COMPLETE')
    );

ALTER TABLE ai_jobs
    ADD CONSTRAINT ai_jobs_generation_shape_check CHECK (
        (job_type = 'IMAGE_ANALYSIS'
            AND base_version_id IS NULL
            AND result_version_id IS NULL
            AND revision_instruction IS NULL)
        OR
        (job_type = 'BLOG_DRAFT_GENERATION'
            AND base_version_id IS NOT NULL
            AND revision_instruction IS NOT NULL
            AND total_items = 1)
    );
