package com.mimir.blog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

final class DraftGenerationApiModels {

    private DraftGenerationApiModels() {
    }

    record CreateDraftGenerationJobRequest(
            @NotNull UUID baseVersionId,
            @NotBlank @Size(max = 10_000) String revisionInstruction,
            DraftGenerationTarget target) {

        CreateDraftGenerationJobRequest {
            target = target == null ? DraftGenerationTarget.FULL : target;
        }
    }

    record DraftRevisionTurnResponse(
            UUID id,
            AiJobStatus status,
            AiJobStage stage,
            UUID baseVersionId,
            UUID resultVersionId,
            String revisionInstruction,
            DraftGenerationTarget target,
            String errorCode,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt) {
    }

    record DraftRevisionTurnPageResponse(
            List<DraftRevisionTurnResponse> items,
            int page,
            int size,
            long totalItems,
            int totalPages) {
    }
}
