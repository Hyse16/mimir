package com.mimir.blog;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "blog_assets")
class BlogAssetEntity {

    @Id
    private UUID id;

    @Column(name = "blog_post_id", nullable = false)
    private UUID blogPostId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BlogAssetEntity() {
    }

    BlogAssetEntity(
            UUID id,
            UUID blogPostId,
            int displayOrder,
            String originalFilename,
            String contentType,
            long byteSize,
            String storageKey,
            Instant createdAt) {
        this.id = id;
        this.blogPostId = blogPostId;
        this.displayOrder = displayOrder;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.byteSize = byteSize;
        this.storageKey = storageKey;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getBlogPostId() {
        return blogPostId;
    }

    int getDisplayOrder() {
        return displayOrder;
    }

    String getOriginalFilename() {
        return originalFilename;
    }

    String getContentType() {
        return contentType;
    }

    long getByteSize() {
        return byteSize;
    }

    String getStorageKey() {
        return storageKey;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
