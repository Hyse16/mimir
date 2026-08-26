package com.mimir.blog;

import static com.mimir.blog.ImageAnalysisApiModels.AiJobResponse;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ImageAnalysisJobController {

    private final ImageAnalysisJobCoordinator coordinator;

    public ImageAnalysisJobController(ImageAnalysisJobCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @PostMapping("/blog-posts/{postId}/generation-jobs")
    public ResponseEntity<AiJobResponse> create(@PathVariable UUID postId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(coordinator.create(postId));
    }

    @GetMapping("/jobs/{jobId}")
    public AiJobResponse detail(@PathVariable UUID jobId) {
        return coordinator.detail(jobId);
    }

    @PostMapping("/jobs/{jobId}/retry-failed")
    public ResponseEntity<AiJobResponse> retryFailed(@PathVariable UUID jobId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(coordinator.retryFailed(jobId));
    }
}
