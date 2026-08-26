package com.mimir.blog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "blog_image_analyses")
class BlogImageAnalysisEntity {

    @Id
    private UUID id;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> objects;

    @Column(name = "visible_text", columnDefinition = "text")
    private String visibleText;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    protected BlogImageAnalysisEntity() {
    }

    BlogImageAnalysisEntity(
            UUID assetId,
            UUID jobId,
            int displayOrder,
            String category,
            String description,
            List<String> objects,
            String visibleText,
            Instant analyzedAt) {
        this.id = UUID.randomUUID();
        this.assetId = assetId;
        this.jobId = jobId;
        this.displayOrder = displayOrder;
        this.category = category;
        this.description = description;
        this.objects = List.copyOf(objects);
        this.visibleText = visibleText;
        this.analyzedAt = analyzedAt;
    }

    UUID getAssetId() {
        return assetId;
    }

    int getDisplayOrder() {
        return displayOrder;
    }

    String getCategory() {
        return category;
    }

    String getDescription() {
        return description;
    }

    List<String> getObjects() {
        return objects;
    }

    String getVisibleText() {
        return visibleText;
    }

    Instant getAnalyzedAt() {
        return analyzedAt;
    }
}
