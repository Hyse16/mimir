package com.mimir.blog;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "blog_contexts")
class BlogContextEntity {

    @Id
    @Column(name = "blog_post_id")
    private UUID blogPostId;

    @Column(name = "visit_context", nullable = false)
    private String visitContext;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BlogContextEntity() {
    }

    BlogContextEntity(UUID blogPostId, String visitContext, Instant now) {
        this.blogPostId = blogPostId;
        this.visitContext = visitContext;
        this.createdAt = now;
        this.updatedAt = now;
    }

    String getVisitContext() {
        return visitContext;
    }

    void update(String value, Instant now) {
        this.visitContext = value;
        this.updatedAt = now;
    }
}
