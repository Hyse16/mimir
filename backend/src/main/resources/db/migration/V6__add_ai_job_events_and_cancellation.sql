ALTER TABLE ai_jobs DROP CONSTRAINT ai_jobs_status_check;
ALTER TABLE ai_jobs
    ADD CONSTRAINT ai_jobs_status_check CHECK (
        status IN ('WAITING', 'RUNNING', 'CANCEL_REQUESTED', 'COMPLETED', 'PARTIAL_FAILED', 'FAILED', 'CANCELLED')
    );
ALTER TABLE ai_jobs ADD COLUMN cancel_requested_at TIMESTAMPTZ;

DROP INDEX ai_jobs_one_active_per_post_idx;
CREATE UNIQUE INDEX ai_jobs_one_active_per_post_idx
    ON ai_jobs (blog_post_id)
    WHERE status IN ('WAITING', 'RUNNING', 'CANCEL_REQUESTED');

ALTER TABLE image_analysis_job_items DROP CONSTRAINT image_analysis_job_items_status_check;
ALTER TABLE image_analysis_job_items
    ADD CONSTRAINT image_analysis_job_items_status_check CHECK (
        status IN ('WAITING', 'SUCCEEDED', 'FAILED', 'CANCELLED')
    );

CREATE TABLE ai_job_events (
    id BIGSERIAL PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES ai_jobs (id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    total_items INTEGER NOT NULL,
    processed_items INTEGER NOT NULL,
    failed_items INTEGER NOT NULL,
    progress INTEGER NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ai_job_events_status_check CHECK (
        status IN ('WAITING', 'RUNNING', 'CANCEL_REQUESTED', 'COMPLETED', 'PARTIAL_FAILED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ai_job_events_stage_check CHECK (stage IN ('QUEUED', 'IMAGE_ANALYSIS', 'COMPLETE')),
    CONSTRAINT ai_job_events_counts_check CHECK (
        total_items > 0 AND processed_items >= 0 AND failed_items >= 0
        AND processed_items + failed_items <= total_items
    ),
    CONSTRAINT ai_job_events_progress_check CHECK (progress BETWEEN 0 AND 100)
);

CREATE INDEX ai_job_events_job_id_idx ON ai_job_events (job_id, id);
