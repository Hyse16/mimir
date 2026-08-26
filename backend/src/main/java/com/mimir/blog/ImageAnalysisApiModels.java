package com.mimir.blog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class ImageAnalysisApiModels {

    private ImageAnalysisApiModels() {
    }

    record ImageAnalysisResponse(
            UUID assetId,
            int displayOrder,
            String category,
            String description,
            List<String> objects,
            String visibleText,
            Instant analyzedAt) {
    }

    record ImageAnalysisItemResponse(
            UUID assetId,
            int displayOrder,
            ImageAnalysisItemStatus status,
            String errorCode,
            ImageAnalysisResponse analysis) {
    }

    record AiJobResponse(
            UUID id,
            UUID blogPostId,
            UUID parentJobId,
            AiJobType jobType,
            AiJobStatus status,
            AiJobStage stage,
            int totalItems,
            int processedItems,
            int failedItems,
            int progress,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Instant cancelRequestedAt,
            List<ImageAnalysisItemResponse> items) {
    }

    record AiJobProgressEventResponse(
            long eventId,
            UUID jobId,
            AiJobStatus status,
            AiJobStage stage,
            int totalItems,
            int processedItems,
            int failedItems,
            int progress,
            Instant occurredAt) {
    }
}
