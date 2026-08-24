package com.mimir.blog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

final class BlogApiModels {

    private BlogApiModels() {
    }

    record CreateBlogPostRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 10_000) String visitContext,
            @Size(max = 100_000) String body,
            @Size(max = 30) List<@NotBlank @Size(max = 50) String> tags) {
    }

    record UpdateBlogPostRequest(
            @Size(min = 1, max = 200) String title,
            BlogPostStatus status,
            @Size(max = 10_000) String visitContext) {
    }

    record CreateDraftVersionRequest(
            @NotNull UUID baseVersionId,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 100_000) String body,
            @Size(max = 30) List<@NotBlank @Size(max = 50) String> tags,
            @Size(max = 10_000) String visitContext,
            @NotNull DraftSource source) {
    }

    record BlogPostSummaryResponse(
            UUID id,
            String title,
            BlogPostStatus status,
            UUID currentVersionId,
            Instant createdAt,
            Instant updatedAt) {
    }

    record DraftVersionResponse(
            UUID id,
            int versionNumber,
            DraftSource source,
            String title,
            String body,
            List<String> tags,
            Instant createdAt,
            boolean selected) {
    }

    record BlogAssetResponse(
            UUID id,
            int displayOrder,
            String originalFilename,
            String contentType,
            long byteSize,
            Instant createdAt) {
    }

    record ReorderBlogAssetsRequest(
            @NotNull @Size(max = 20) List<@NotNull UUID> assetIds) {
    }

    record BlogPostDetailResponse(
            UUID id,
            String title,
            BlogPostStatus status,
            String visitContext,
            UUID currentVersionId,
            Instant createdAt,
            Instant updatedAt,
            DraftVersionResponse currentVersion,
            List<DraftVersionResponse> versions,
            List<BlogAssetResponse> assets) {
    }

    record PageResponse<T>(
            List<T> items,
            int page,
            int size,
            long totalItems,
            int totalPages) {
    }
}
