package com.mimir.blog;

import static com.mimir.blog.BlogApiModels.BlogPostDetailResponse;
import static com.mimir.blog.BlogApiModels.BlogPostSummaryResponse;
import static com.mimir.blog.BlogApiModels.CreateBlogPostRequest;
import static com.mimir.blog.BlogApiModels.CreateDraftVersionRequest;
import static com.mimir.blog.BlogApiModels.PageResponse;
import static com.mimir.blog.BlogApiModels.UpdateBlogPostRequest;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/blog-posts")
public class BlogPostController {

    private final BlogPostService service;

    public BlogPostController(BlogPostService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BlogPostDetailResponse> create(@Valid @RequestBody CreateBlogPostRequest request) {
        BlogPostDetailResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/blog-posts/" + response.id())).body(response);
    }

    @GetMapping
    public PageResponse<BlogPostSummaryResponse> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) BlogPostStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return service.list(query, status, page, size, sort, direction);
    }

    @GetMapping("/{postId}")
    public BlogPostDetailResponse detail(@PathVariable UUID postId) {
        return service.detail(postId);
    }

    @PatchMapping("/{postId}")
    public BlogPostDetailResponse update(
            @PathVariable UUID postId,
            @Valid @RequestBody UpdateBlogPostRequest request) {
        return service.update(postId, request);
    }

    @PostMapping("/{postId}/versions")
    public ResponseEntity<BlogPostDetailResponse> addVersion(
            @PathVariable UUID postId,
            @Valid @RequestBody CreateDraftVersionRequest request) {
        return ResponseEntity.status(201).body(service.addVersion(postId, request));
    }

    @PostMapping("/{postId}/versions/{versionId}/select")
    public BlogPostDetailResponse selectVersion(
            @PathVariable UUID postId,
            @PathVariable UUID versionId) {
        return service.selectVersion(postId, versionId);
    }

    @PostMapping("/{postId}/archive")
    public BlogPostDetailResponse archive(@PathVariable UUID postId) {
        return service.archive(postId);
    }

    @PostMapping("/{postId}/duplicate")
    public ResponseEntity<BlogPostDetailResponse> duplicate(@PathVariable UUID postId) {
        BlogPostDetailResponse response = service.duplicate(postId);
        return ResponseEntity.created(URI.create("/api/v1/blog-posts/" + response.id())).body(response);
    }
}
