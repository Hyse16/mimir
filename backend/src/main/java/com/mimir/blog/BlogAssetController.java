package com.mimir.blog;

import static com.mimir.blog.BlogApiModels.BlogAssetResponse;
import static com.mimir.blog.BlogApiModels.ReorderBlogAssetsRequest;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@Validated
@RestController
@RequestMapping("/api/v1/blog-posts/{postId}/assets")
public class BlogAssetController {

    private final BlogAssetService service;

    public BlogAssetController(BlogAssetService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<BlogAssetResponse>> upload(
            @PathVariable UUID postId,
            @RequestPart("files") @Size(min = 1, max = BlogAssetService.MAX_IMAGES_PER_POST) List<MultipartFile> files) {
        return ResponseEntity.status(201).body(service.upload(postId, files));
    }

    @PutMapping("/order")
    public List<BlogAssetResponse> reorder(
            @PathVariable UUID postId,
            @Valid @RequestBody ReorderBlogAssetsRequest request) {
        return service.reorder(postId, request.assetIds());
    }

    @DeleteMapping("/{assetId}")
    public List<BlogAssetResponse> delete(@PathVariable UUID postId, @PathVariable UUID assetId) {
        return service.delete(postId, assetId);
    }
}
