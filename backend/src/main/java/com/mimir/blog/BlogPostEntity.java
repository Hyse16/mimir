package com.mimir.blog;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "blog_posts")
class BlogPostEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BlogPostStatus status;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BlogPostEntity() {
    }

    BlogPostEntity(UUID id, String title, Instant now) {
        this.id = id;
        this.title = title;
        this.status = BlogPostStatus.DRAFT;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID getId() {
        return id;
    }

    String getTitle() {
        return title;
    }

    BlogPostStatus getStatus() {
        return status;
    }

    UUID getCurrentVersionId() {
        return currentVersionId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void selectVersion(UUID versionId, String selectedTitle, Instant now) {
        this.currentVersionId = versionId;
        this.title = selectedTitle;
        this.updatedAt = now;
    }

    void updateMetadata(String updatedTitle, BlogPostStatus updatedStatus, Instant now) {
        if (updatedTitle != null) {
            this.title = updatedTitle;
        }
        if (updatedStatus != null) {
            this.status = updatedStatus;
        }
        this.updatedAt = now;
    }

    void archive(Instant now) {
        this.status = BlogPostStatus.ARCHIVED;
        this.updatedAt = now;
    }

    void startGeneration(Instant now) {
        status = BlogPostStatus.GENERATING;
        updatedAt = now;
    }

    void finishGeneration(Instant now) {
        status = BlogPostStatus.REVIEW_REQUIRED;
        updatedAt = now;
    }

    void failGeneration(Instant now) {
        if (status == BlogPostStatus.GENERATING) {
            status = BlogPostStatus.REVIEW_REQUIRED;
            updatedAt = now;
        }
    }

    void touch(Instant now) {
        this.updatedAt = now;
    }
}
