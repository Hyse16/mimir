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
@Table(name = "image_analysis_job_items")
class ImageAnalysisJobItemEntity {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ImageAnalysisItemStatus status;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ImageAnalysisJobItemEntity() {
    }

    ImageAnalysisJobItemEntity(UUID id, UUID jobId, UUID assetId, int displayOrder, Instant createdAt) {
        this.id = id;
        this.jobId = jobId;
        this.assetId = assetId;
        this.displayOrder = displayOrder;
        this.status = ImageAnalysisItemStatus.WAITING;
        this.createdAt = createdAt;
    }

    void succeed(Instant now) {
        status = ImageAnalysisItemStatus.SUCCEEDED;
        errorCode = null;
        completedAt = now;
    }

    void fail(String code, Instant now) {
        status = ImageAnalysisItemStatus.FAILED;
        errorCode = code;
        completedAt = now;
    }

    UUID getJobId() {
        return jobId;
    }

    UUID getAssetId() {
        return assetId;
    }

    int getDisplayOrder() {
        return displayOrder;
    }

    ImageAnalysisItemStatus getStatus() {
        return status;
    }

    String getErrorCode() {
        return errorCode;
    }
}
