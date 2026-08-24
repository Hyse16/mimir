CREATE TABLE blog_assets (
    id UUID PRIMARY KEY,
    blog_post_id UUID NOT NULL REFERENCES blog_posts (id) ON DELETE CASCADE,
    display_order INTEGER NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT blog_assets_order_check CHECK (display_order >= 0),
    CONSTRAINT blog_assets_size_check CHECK (byte_size > 0),
    CONSTRAINT blog_assets_post_order_unique UNIQUE (blog_post_id, display_order),
    CONSTRAINT blog_assets_storage_key_unique UNIQUE (storage_key)
);

CREATE INDEX blog_assets_post_created_idx ON blog_assets (blog_post_id, created_at);
