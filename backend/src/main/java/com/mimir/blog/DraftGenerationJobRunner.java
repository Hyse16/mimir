package com.mimir.blog;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mimir.ai.TextGenerationGateway;

@Component
class DraftGenerationJobRunner {

    private final DraftGenerationJobService service;
    private final TextGenerationGateway gateway;

    DraftGenerationJobRunner(DraftGenerationJobService service, TextGenerationGateway gateway) {
        this.service = service;
        this.gateway = gateway;
    }

    void run(UUID jobId) {
        boolean started = false;
        try {
            var request = service.start(jobId);
            if (request == null) {
                return;
            }
            started = true;
            service.complete(jobId, gateway.generate(request));
        } catch (RuntimeException error) {
            if (started) {
                service.fail(jobId, "TEXT_GENERATION_FAILED");
                return;
            }
            throw error;
        }
    }
}
