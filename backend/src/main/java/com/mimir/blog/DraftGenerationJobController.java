package com.mimir.blog;

import static com.mimir.blog.DraftGenerationApiModels.CreateDraftGenerationJobRequest;
import static com.mimir.blog.ImageAnalysisApiModels.AiJobResponse;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/blog-posts")
public class DraftGenerationJobController {

    private final DraftGenerationJobCoordinator coordinator;

    public DraftGenerationJobController(DraftGenerationJobCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @PostMapping("/{postId}/draft-generation-jobs")
    public ResponseEntity<AiJobResponse> create(
            @PathVariable UUID postId,
            @Valid @RequestBody CreateDraftGenerationJobRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(coordinator.create(
                postId, request.baseVersionId(), request.revisionInstruction()));
    }
}
