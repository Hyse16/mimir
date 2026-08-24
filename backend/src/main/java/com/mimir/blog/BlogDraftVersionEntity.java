package com.mimir.blog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(name = "blog_draft_versions")
class BlogDraftVersionEntity {

    @Id
    private UUID id;

    @Column(name = "blog_post_id", nullable = false)
    private UUID blogPostId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DraftSource source;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private String body;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "blog_draft_version_tags",
            joinColumns = @JoinColumn(name = "version_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "tag", nullable = false, length = 50)
    private List<String> tags = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BlogDraftVersionEntity() {
    }

    BlogDraftVersionEntity(
            UUID id,
            UUID blogPostId,
            int versionNumber,
            DraftSource source,
            String title,
            String body,
            List<String> tags,
            Instant createdAt) {
        this.id = id;
        this.blogPostId = blogPostId;
        this.versionNumber = versionNumber;
        this.source = source;
        this.title = title;
        this.body = body;
        this.tags = new ArrayList<>(tags);
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getBlogPostId() {
        return blogPostId;
    }

    int getVersionNumber() {
        return versionNumber;
    }

    DraftSource getSource() {
        return source;
    }

    String getTitle() {
        return title;
    }

    String getBody() {
        return body;
    }

    List<String> getTags() {
        return List.copyOf(tags);
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
