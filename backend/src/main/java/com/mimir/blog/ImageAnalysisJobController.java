package com.mimir.blog;

import static com.mimir.blog.ImageAnalysisApiModels.AiJobResponse;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
public class ImageAnalysisJobController {

    private final ImageAnalysisJobCoordinator coordinator;
    private final ImageAnalysisJobEventStreamService eventStreamService;

    public ImageAnalysisJobController(
            ImageAnalysisJobCoordinator coordinator,
            ImageAnalysisJobEventStreamService eventStreamService) {
        this.coordinator = coordinator;
        this.eventStreamService = eventStreamService;
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

    @PostMapping("/jobs/{jobId}/cancel")
    public ResponseEntity<AiJobResponse> cancel(@PathVariable UUID jobId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(coordinator.cancel(jobId));
    }

    @GetMapping(path = "/jobs/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @PathVariable UUID jobId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return eventStreamService.stream(jobId, parseLastEventId(lastEventId));
    }

    private long parseLastEventId(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            long eventId = Long.parseLong(value);
            if (eventId < 0) {
                throw new IllegalArgumentException("Last-Event-ID must be zero or greater.");
            }
            return eventId;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Last-Event-ID must be a number.", error);
        }
    }
}
