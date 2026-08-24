CREATE TABLE blog_posts (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT blog_posts_status_check CHECK (
        status IN ('DRAFT', 'GENERATING', 'REVIEW_REQUIRED', 'READY', 'PUBLISHED', 'ARCHIVED')
    )
);

CREATE INDEX blog_posts_status_updated_idx ON blog_posts (status, updated_at DESC);
CREATE INDEX blog_posts_updated_idx ON blog_posts (updated_at DESC);

CREATE TABLE blog_contexts (
    blog_post_id UUID PRIMARY KEY REFERENCES blog_posts (id) ON DELETE CASCADE,
    visit_context TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE blog_draft_versions (
    id UUID PRIMARY KEY,
    blog_post_id UUID NOT NULL REFERENCES blog_posts (id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    source VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT blog_draft_versions_number_check CHECK (version_number > 0),
    CONSTRAINT blog_draft_versions_source_check CHECK (source IN ('USER_EDIT', 'AI_GENERATED')),
    CONSTRAINT blog_draft_versions_post_number_unique UNIQUE (blog_post_id, version_number)
);

CREATE INDEX blog_draft_versions_post_created_idx
    ON blog_draft_versions (blog_post_id, created_at DESC);

CREATE TABLE blog_draft_version_tags (
    version_id UUID NOT NULL REFERENCES blog_draft_versions (id) ON DELETE CASCADE,
    display_order INTEGER NOT NULL,
    tag VARCHAR(50) NOT NULL,
    PRIMARY KEY (version_id, display_order),
    CONSTRAINT blog_draft_version_tags_order_check CHECK (display_order >= 0)
);

ALTER TABLE blog_posts
    ADD CONSTRAINT blog_posts_current_version_fk
    FOREIGN KEY (current_version_id) REFERENCES blog_draft_versions (id);
