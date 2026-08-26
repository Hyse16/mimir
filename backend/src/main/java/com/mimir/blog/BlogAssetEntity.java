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

    @Column(name = "original_width")
    private Integer originalWidth;

    @Column(name = "original_height")
    private Integer originalHeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "derivative_status", nullable = false, length = 32)
    private BlogAssetDerivativeStatus derivativeStatus;

    @Column(name = "optimized_storage_key", length = 500)
    private String optimizedStorageKey;

    @Column(name = "optimized_content_type", length = 64)
    private String optimizedContentType;

    @Column(name = "optimized_byte_size")
    private Long optimizedByteSize;

    @Column(name = "optimized_width")
    private Integer optimizedWidth;

    @Column(name = "optimized_height")
    private Integer optimizedHeight;

    @Column(name = "analysis_storage_key", length = 500)
    private String analysisStorageKey;

    @Column(name = "analysis_content_type", length = 64)
    private String analysisContentType;

    @Column(name = "analysis_byte_size")
    private Long analysisByteSize;

    @Column(name = "analysis_width")
    private Integer analysisWidth;

    @Column(name = "analysis_height")
    private Integer analysisHeight;

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
            Integer originalWidth,
            Integer originalHeight,
            BlogAssetDerivativeStatus derivativeStatus,
            String optimizedStorageKey,
            String optimizedContentType,
            Long optimizedByteSize,
            Integer optimizedWidth,
            Integer optimizedHeight,
            String analysisStorageKey,
            String analysisContentType,
            Long analysisByteSize,
            Integer analysisWidth,
            Integer analysisHeight,
            Instant createdAt) {
        this.id = id;
        this.blogPostId = blogPostId;
        this.displayOrder = displayOrder;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.byteSize = byteSize;
        this.storageKey = storageKey;
        this.originalWidth = originalWidth;
        this.originalHeight = originalHeight;
        this.derivativeStatus = derivativeStatus;
        this.optimizedStorageKey = optimizedStorageKey;
        this.optimizedContentType = optimizedContentType;
        this.optimizedByteSize = optimizedByteSize;
        this.optimizedWidth = optimizedWidth;
        this.optimizedHeight = optimizedHeight;
        this.analysisStorageKey = analysisStorageKey;
        this.analysisContentType = analysisContentType;
        this.analysisByteSize = analysisByteSize;
        this.analysisWidth = analysisWidth;
        this.analysisHeight = analysisHeight;
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

    Integer getOriginalWidth() {
        return originalWidth;
    }

    Integer getOriginalHeight() {
        return originalHeight;
    }

    BlogAssetDerivativeStatus getDerivativeStatus() {
        return derivativeStatus;
    }

    String getOptimizedStorageKey() {
        return optimizedStorageKey;
    }

    String getOptimizedContentType() {
        return optimizedContentType;
    }

    Long getOptimizedByteSize() {
        return optimizedByteSize;
    }

    Integer getOptimizedWidth() {
        return optimizedWidth;
    }

    Integer getOptimizedHeight() {
        return optimizedHeight;
    }

    String getAnalysisStorageKey() {
        return analysisStorageKey;
    }

    String getAnalysisContentType() {
        return analysisContentType;
    }

    Long getAnalysisByteSize() {
        return analysisByteSize;
    }

    Integer getAnalysisWidth() {
        return analysisWidth;
    }

    Integer getAnalysisHeight() {
        return analysisHeight;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
