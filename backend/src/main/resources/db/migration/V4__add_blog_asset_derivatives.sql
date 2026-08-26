ALTER TABLE blog_assets
    ADD COLUMN original_width INTEGER,
    ADD COLUMN original_height INTEGER,
    ADD COLUMN derivative_status VARCHAR(32) NOT NULL DEFAULT 'ORIGINAL_ONLY',
    ADD COLUMN optimized_storage_key VARCHAR(500),
    ADD COLUMN optimized_content_type VARCHAR(64),
    ADD COLUMN optimized_byte_size BIGINT,
    ADD COLUMN optimized_width INTEGER,
    ADD COLUMN optimized_height INTEGER,
    ADD COLUMN analysis_storage_key VARCHAR(500),
    ADD COLUMN analysis_content_type VARCHAR(64),
    ADD COLUMN analysis_byte_size BIGINT,
    ADD COLUMN analysis_width INTEGER,
    ADD COLUMN analysis_height INTEGER,
    ADD CONSTRAINT blog_assets_original_dimensions_check CHECK (
        (original_width IS NULL AND original_height IS NULL)
        OR (original_width > 0 AND original_height > 0)
    ),
    ADD CONSTRAINT blog_assets_derivative_status_check CHECK (
        derivative_status IN ('READY', 'ORIGINAL_ONLY')
    ),
    ADD CONSTRAINT blog_assets_optimized_metadata_check CHECK (
        (optimized_storage_key IS NULL AND optimized_content_type IS NULL AND optimized_byte_size IS NULL
            AND optimized_width IS NULL AND optimized_height IS NULL)
        OR (optimized_storage_key IS NOT NULL AND optimized_content_type IS NOT NULL AND optimized_byte_size > 0
            AND optimized_width > 0 AND optimized_height > 0)
    ),
    ADD CONSTRAINT blog_assets_analysis_metadata_check CHECK (
        (analysis_storage_key IS NULL AND analysis_content_type IS NULL AND analysis_byte_size IS NULL
            AND analysis_width IS NULL AND analysis_height IS NULL)
        OR (analysis_storage_key IS NOT NULL AND analysis_content_type IS NOT NULL AND analysis_byte_size > 0
            AND analysis_width > 0 AND analysis_height > 0)
    ),
    ADD CONSTRAINT blog_assets_ready_derivatives_check CHECK (
        derivative_status <> 'READY'
        OR (original_width IS NOT NULL AND optimized_storage_key IS NOT NULL AND analysis_storage_key IS NOT NULL)
    );

CREATE UNIQUE INDEX blog_assets_optimized_storage_key_unique
    ON blog_assets (optimized_storage_key)
    WHERE optimized_storage_key IS NOT NULL;

CREATE UNIQUE INDEX blog_assets_analysis_storage_key_unique
    ON blog_assets (analysis_storage_key)
    WHERE analysis_storage_key IS NOT NULL;
