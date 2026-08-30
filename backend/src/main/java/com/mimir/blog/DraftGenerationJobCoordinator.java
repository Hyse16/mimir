package com.mimir.blog;

import static com.mimir.blog.ImageAnalysisApiModels.AiJobResponse;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
class DraftGenerationJobCoordinator {

    private final DraftGenerationJobService service;
    private final ImageAnalysisJobService queryService;
    private final DraftGenerationJobRunner runner;
    private final TaskExecutor taskExecutor;

    DraftGenerationJobCoordinator(
            DraftGenerationJobService service,
            ImageAnalysisJobService queryService,
            DraftGenerationJobRunner runner,
            @Qualifier("localAiTaskExecutor") TaskExecutor taskExecutor) {
        this.service = service;
        this.queryService = queryService;
        this.runner = runner;
        this.taskExecutor = taskExecutor;
    }

    AiJobResponse create(
            UUID postId,
            UUID baseVersionId,
            String revisionInstruction,
            DraftGenerationTarget target) {
        UUID jobId = service.create(postId, baseVersionId, revisionInstruction, target);
        taskExecutor.execute(() -> runner.run(jobId));
        return queryService.detail(jobId);
    }

    DraftGenerationApiModels.DraftRevisionTurnPageResponse history(UUID postId, int page, int size) {
        return service.history(postId, page, size);
    }
}
