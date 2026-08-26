package com.mimir.blog;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_job_events")
class AiJobEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

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

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AiJobEventEntity() {
    }

    AiJobEventEntity(AiJobEntity job, Instant occurredAt) {
        this.jobId = job.getId();
        this.status = job.getStatus();
        this.stage = job.getStage();
        this.totalItems = job.getTotalItems();
        this.processedItems = job.getProcessedItems();
        this.failedItems = job.getFailedItems();
        this.progress = job.getProgress();
        this.occurredAt = occurredAt;
    }

    Long getId() {
        return id;
    }

    UUID getJobId() {
        return jobId;
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

    Instant getOccurredAt() {
        return occurredAt;
    }
}
