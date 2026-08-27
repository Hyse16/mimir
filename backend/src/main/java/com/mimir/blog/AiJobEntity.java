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
@Table(name = "ai_jobs")
class AiJobEntity {

    @Id
    private UUID id;

    @Column(name = "blog_post_id", nullable = false)
    private UUID blogPostId;

    @Column(name = "parent_job_id")
    private UUID parentJobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 32)
    private AiJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiJobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiJobStage stage;

    @Column(name = "total_items", nullable = false)
    private int totalItems;

    @Column(name = "processed_items", nullable = false)
    private int processedItems;

    @Column(name = "failed_items", nullable = false)
    private int failedItems;

    @Column(nullable = false)
    private int progress;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancel_requested_at")
    private Instant cancelRequestedAt;

    @Column(name = "base_version_id")
    private UUID baseVersionId;

    @Column(name = "result_version_id")
    private UUID resultVersionId;

    @Column(name = "revision_instruction", columnDefinition = "text")
    private String revisionInstruction;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    protected AiJobEntity() {
    }

    AiJobEntity(UUID id, UUID blogPostId, UUID parentJobId, int totalItems, Instant createdAt) {
        this.id = id;
        this.blogPostId = blogPostId;
        this.parentJobId = parentJobId;
        this.jobType = AiJobType.IMAGE_ANALYSIS;
        this.status = AiJobStatus.WAITING;
        this.stage = AiJobStage.QUEUED;
        this.totalItems = totalItems;
        this.processedItems = 0;
        this.failedItems = 0;
        this.progress = 0;
        this.createdAt = createdAt;
    }

    AiJobEntity(
            UUID id,
            UUID blogPostId,
            UUID baseVersionId,
            String revisionInstruction,
            Instant createdAt) {
        this.id = id;
        this.blogPostId = blogPostId;
        this.jobType = AiJobType.BLOG_DRAFT_GENERATION;
        this.status = AiJobStatus.WAITING;
        this.stage = AiJobStage.QUEUED;
        this.totalItems = 1;
        this.processedItems = 0;
        this.failedItems = 0;
        this.progress = 0;
        this.createdAt = createdAt;
        this.baseVersionId = baseVersionId;
        this.revisionInstruction = revisionInstruction;
    }

    void start(Instant now) {
        status = AiJobStatus.RUNNING;
        stage = AiJobStage.IMAGE_ANALYSIS;
        startedAt = now;
    }

    void start(AiJobStage runningStage, Instant now) {
        status = AiJobStatus.RUNNING;
        stage = runningStage;
        startedAt = now;
    }

    void advanceStage(AiJobStage nextStage) {
        stage = nextStage;
    }

    void requestCancellation(Instant now) {
        status = AiJobStatus.CANCEL_REQUESTED;
        cancelRequestedAt = now;
    }

    void cancel(Instant now) {
        status = AiJobStatus.CANCELLED;
        stage = AiJobStage.COMPLETE;
        completedAt = now;
    }

    void recordBatch(int succeeded, int failed, Instant now) {
        processedItems += succeeded;
        failedItems += failed;
        progress = Math.min(100, (processedItems + failedItems) * 100 / totalItems);
        if (processedItems + failedItems == totalItems) {
            stage = AiJobStage.COMPLETE;
            completedAt = now;
            progress = 100;
            status = failedItems == 0
                    ? AiJobStatus.COMPLETED
                    : processedItems == 0 ? AiJobStatus.FAILED : AiJobStatus.PARTIAL_FAILED;
        }
    }

    void completeDraft(UUID versionId, Instant now) {
        resultVersionId = versionId;
        processedItems = 1;
        progress = 100;
        status = AiJobStatus.COMPLETED;
        stage = AiJobStage.COMPLETE;
        completedAt = now;
    }

    void fail(String code, Instant now) {
        errorCode = code;
        failedItems = 1;
        progress = 100;
        status = AiJobStatus.FAILED;
        stage = AiJobStage.COMPLETE;
        completedAt = now;
    }

    UUID getId() {
        return id;
    }

    UUID getBlogPostId() {
        return blogPostId;
    }

    UUID getParentJobId() {
        return parentJobId;
    }

    AiJobType getJobType() {
        return jobType;
    }

    AiJobStatus getStatus() {
        return status;
    }

    AiJobStage getStage() {
        return stage;
    }

    int getTotalItems() {
        return totalItems;
    }

    int getProcessedItems() {
        return processedItems;
    }

    int getFailedItems() {
        return failedItems;
    }

    int getProgress() {
        return progress;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getStartedAt() {
        return startedAt;
    }

    Instant getCompletedAt() {
        return completedAt;
    }

    Instant getCancelRequestedAt() {
        return cancelRequestedAt;
    }

    UUID getBaseVersionId() {
        return baseVersionId;
    }

    UUID getResultVersionId() {
        return resultVersionId;
    }

    String getRevisionInstruction() {
        return revisionInstruction;
    }

    String getErrorCode() {
        return errorCode;
    }
}
