package com.mimir.blog;

import static com.mimir.blog.ImageAnalysisApiModels.AiJobResponse;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
class ImageAnalysisJobCoordinator {

    private final ImageAnalysisJobService service;
    private final ImageAnalysisJobRunner runner;
    private final TaskExecutor taskExecutor;

    ImageAnalysisJobCoordinator(
            ImageAnalysisJobService service,
            ImageAnalysisJobRunner runner,
            @Qualifier("localAiTaskExecutor") TaskExecutor taskExecutor) {
        this.service = service;
        this.runner = runner;
        this.taskExecutor = taskExecutor;
    }

    AiJobResponse create(UUID postId) {
        UUID jobId = service.create(postId);
        taskExecutor.execute(() -> runner.run(jobId));
        return service.detail(jobId);
    }

    AiJobResponse retryFailed(UUID jobId) {
        UUID retryJobId = service.retryFailed(jobId);
        taskExecutor.execute(() -> runner.run(retryJobId));
        return service.detail(retryJobId);
    }

    AiJobResponse detail(UUID jobId) {
        return service.detail(jobId);
    }

    AiJobResponse cancel(UUID jobId) {
        service.requestCancellation(jobId);
        return service.detail(jobId);
    }
}
