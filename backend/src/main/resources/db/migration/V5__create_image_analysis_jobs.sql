CREATE TABLE ai_jobs (
    id UUID PRIMARY KEY,
    blog_post_id UUID NOT NULL REFERENCES blog_posts (id) ON DELETE CASCADE,
    parent_job_id UUID REFERENCES ai_jobs (id),
    job_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    total_items INTEGER NOT NULL,
    processed_items INTEGER NOT NULL DEFAULT 0,
    failed_items INTEGER NOT NULL DEFAULT 0,
    progress INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    CONSTRAINT ai_jobs_type_check CHECK (job_type IN ('IMAGE_ANALYSIS')),
    CONSTRAINT ai_jobs_status_check CHECK (status IN ('WAITING', 'RUNNING', 'COMPLETED', 'PARTIAL_FAILED', 'FAILED')),
    CONSTRAINT ai_jobs_stage_check CHECK (stage IN ('QUEUED', 'IMAGE_ANALYSIS', 'COMPLETE')),
    CONSTRAINT ai_jobs_counts_check CHECK (
        total_items > 0 AND processed_items >= 0 AND failed_items >= 0
        AND processed_items + failed_items <= total_items
    ),
    CONSTRAINT ai_jobs_progress_check CHECK (progress BETWEEN 0 AND 100)
);

CREATE INDEX ai_jobs_post_created_idx ON ai_jobs (blog_post_id, created_at DESC);
CREATE UNIQUE INDEX ai_jobs_one_active_per_post_idx
    ON ai_jobs (blog_post_id)
    WHERE status IN ('WAITING', 'RUNNING');

CREATE TABLE image_analysis_job_items (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES ai_jobs (id) ON DELETE CASCADE,
    asset_id UUID NOT NULL,
    display_order INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT image_analysis_job_items_status_check CHECK (status IN ('WAITING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT image_analysis_job_items_order_check CHECK (display_order >= 0),
    CONSTRAINT image_analysis_job_items_job_asset_unique UNIQUE (job_id, asset_id)
);

CREATE INDEX image_analysis_job_items_job_order_idx
    ON image_analysis_job_items (job_id, display_order);

CREATE TABLE blog_image_analyses (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES blog_assets (id) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES ai_jobs (id),
    display_order INTEGER NOT NULL,
    category VARCHAR(64) NOT NULL,
    description TEXT NOT NULL,
    objects JSONB NOT NULL,
    visible_text TEXT,
    analyzed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT blog_image_analyses_order_check CHECK (display_order >= 0),
    CONSTRAINT blog_image_analyses_objects_array_check CHECK (jsonb_typeof(objects) = 'array'),
    CONSTRAINT blog_image_analyses_job_asset_unique UNIQUE (job_id, asset_id)
);

CREATE INDEX blog_image_analyses_job_idx ON blog_image_analyses (job_id);
