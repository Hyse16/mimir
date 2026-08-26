package com.mimir.blog;

import static com.mimir.blog.ImageAnalysisApiModels.AiJobResponse;
import static com.mimir.blog.ImageAnalysisApiModels.ImageAnalysisItemResponse;
import static com.mimir.blog.ImageAnalysisApiModels.ImageAnalysisResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mimir.ai.VisionAnalysisGateway.VisionAnalysis;

@Service
class ImageAnalysisJobService {

    private final BlogPostRepository postRepository;
    private final BlogAssetRepository assetRepository;
    private final AiJobRepository jobRepository;
    private final ImageAnalysisJobItemRepository itemRepository;
    private final BlogImageAnalysisRepository analysisRepository;
    private final Clock clock;

    @Autowired
    ImageAnalysisJobService(
            BlogPostRepository postRepository,
            BlogAssetRepository assetRepository,
            AiJobRepository jobRepository,
            ImageAnalysisJobItemRepository itemRepository,
            BlogImageAnalysisRepository analysisRepository) {
        this(postRepository, assetRepository, jobRepository, itemRepository, analysisRepository, Clock.systemUTC());
    }

    ImageAnalysisJobService(
            BlogPostRepository postRepository,
            BlogAssetRepository assetRepository,
            AiJobRepository jobRepository,
            ImageAnalysisJobItemRepository itemRepository,
            BlogImageAnalysisRepository analysisRepository,
            Clock clock) {
        this.postRepository = postRepository;
        this.assetRepository = assetRepository;
        this.jobRepository = jobRepository;
        this.itemRepository = itemRepository;
        this.analysisRepository = analysisRepository;
        this.clock = clock;
    }

    @Transactional
    public UUID create(UUID postId) {
        if (!postRepository.existsById(postId)) {
            throw new BlogNotFoundException(postId);
        }
        List<BlogAssetEntity> assets = assetRepository.findByBlogPostIdOrderByDisplayOrderAsc(postId);
        if (assets.isEmpty()) {
            throw new InvalidAiJobOperationException("At least one image is required for analysis.");
        }
        requireNoActiveJob(postId);
        return createJob(postId, null, assets.stream()
                .map(asset -> new JobTarget(asset.getId(), asset.getDisplayOrder()))
                .toList());
    }

    @Transactional
    public UUID retryFailed(UUID parentJobId) {
        AiJobEntity parent = requiredJob(parentJobId);
        if (parent.getStatus() != AiJobStatus.PARTIAL_FAILED && parent.getStatus() != AiJobStatus.FAILED) {
            throw new InvalidAiJobOperationException("Only failed or partially failed jobs can be retried.");
        }
        List<JobTarget> targets = itemRepository
                .findByJobIdAndStatusOrderByDisplayOrderAsc(parentJobId, ImageAnalysisItemStatus.FAILED)
                .stream()
                .map(item -> new JobTarget(item.getAssetId(), item.getDisplayOrder()))
                .toList();
        if (targets.isEmpty()) {
            throw new InvalidAiJobOperationException("The job has no failed image items to retry.");
        }
        requireNoActiveJob(parent.getBlogPostId());
        return createJob(parent.getBlogPostId(), parentJobId, targets);
    }

    @Transactional
    public void start(UUID jobId) {
        AiJobEntity job = requiredJob(jobId);
        if (job.getStatus() != AiJobStatus.WAITING) {
            throw new InvalidAiJobOperationException("Only waiting jobs can start.");
        }
        job.start(clock.instant());
    }

    @Transactional(readOnly = true)
    public List<AnalysisWorkItem> pendingItems(UUID jobId) {
        requiredJob(jobId);
        List<ImageAnalysisJobItemEntity> items = itemRepository.findByJobIdAndStatusOrderByDisplayOrderAsc(
                jobId, ImageAnalysisItemStatus.WAITING);
        Map<UUID, BlogAssetEntity> assets = assetRepository.findAllById(items.stream()
                        .map(ImageAnalysisJobItemEntity::getAssetId).toList())
                .stream()
                .collect(Collectors.toMap(BlogAssetEntity::getId, Function.identity()));
        return items.stream()
                .map(item -> {
                    BlogAssetEntity asset = assets.get(item.getAssetId());
                    return new AnalysisWorkItem(
                            item.getAssetId(),
                            item.getDisplayOrder(),
                            asset == null ? null : asset.getAnalysisStorageKey());
                })
                .toList();
    }

    @Transactional
    public void completeBatch(UUID jobId, List<VisionAnalysis> successes, Map<UUID, String> failures) {
        AiJobEntity job = requiredJob(jobId);
        Instant now = clock.instant();
        Map<UUID, ImageAnalysisJobItemEntity> items = itemRepository
                .findByJobIdOrderByDisplayOrderAsc(jobId)
                .stream()
                .collect(Collectors.toMap(ImageAnalysisJobItemEntity::getAssetId, Function.identity()));
        List<BlogImageAnalysisEntity> analyses = new ArrayList<>();
        for (VisionAnalysis result : successes) {
            ImageAnalysisJobItemEntity item = requiredWaitingItem(items, result.assetId());
            analyses.add(new BlogImageAnalysisEntity(
                    result.assetId(),
                    jobId,
                    result.displayOrder(),
                    result.category(),
                    result.description(),
                    result.objects(),
                    result.visibleText(),
                    now));
            item.succeed(now);
        }
        for (Map.Entry<UUID, String> failure : failures.entrySet()) {
            requiredWaitingItem(items, failure.getKey()).fail(failure.getValue(), now);
        }
        analysisRepository.saveAll(analyses);
        job.recordBatch(successes.size(), failures.size(), now);
    }

    @Transactional
    public void failRemaining(UUID jobId, String errorCode) {
        AiJobEntity job = requiredJob(jobId);
        if (job.getStatus() != AiJobStatus.RUNNING) {
            return;
        }
        Instant now = clock.instant();
        List<ImageAnalysisJobItemEntity> waiting = itemRepository.findByJobIdAndStatusOrderByDisplayOrderAsc(
                jobId, ImageAnalysisItemStatus.WAITING);
        waiting.forEach(item -> item.fail(errorCode, now));
        if (!waiting.isEmpty()) {
            job.recordBatch(0, waiting.size(), now);
        }
    }

    @Transactional(readOnly = true)
    public AiJobResponse detail(UUID jobId) {
        AiJobEntity job = requiredJob(jobId);
        List<ImageAnalysisJobItemEntity> items = itemRepository.findByJobIdOrderByDisplayOrderAsc(jobId);
        Map<UUID, BlogImageAnalysisEntity> analyses = analysisRepository.findByJobId(jobId)
                .stream()
                .collect(Collectors.toMap(BlogImageAnalysisEntity::getAssetId, Function.identity()));
        return toResponse(job, items, analyses);
    }

    private UUID createJob(UUID postId, UUID parentJobId, List<JobTarget> targets) {
        Instant now = clock.instant();
        UUID jobId = UUID.randomUUID();
        jobRepository.save(new AiJobEntity(jobId, postId, parentJobId, targets.size(), now));
        itemRepository.saveAll(targets.stream()
                .map(target -> new ImageAnalysisJobItemEntity(
                        UUID.randomUUID(), jobId, target.assetId(), target.displayOrder(), now))
                .toList());
        return jobId;
    }

    private void requireNoActiveJob(UUID postId) {
        if (jobRepository.existsByBlogPostIdAndStatusIn(
                postId,
                List.of(AiJobStatus.WAITING, AiJobStatus.RUNNING))) {
            throw new InvalidAiJobOperationException("An image analysis job is already active for this post.");
        }
    }

    private AiJobEntity requiredJob(UUID jobId) {
        return jobRepository.findById(jobId).orElseThrow(() -> new AiJobNotFoundException(jobId));
    }

    private ImageAnalysisJobItemEntity requiredWaitingItem(
            Map<UUID, ImageAnalysisJobItemEntity> items,
            UUID assetId) {
        ImageAnalysisJobItemEntity item = items.get(assetId);
        if (item == null || item.getStatus() != ImageAnalysisItemStatus.WAITING) {
            throw new InvalidAiJobOperationException("Image analysis item is not waiting: " + assetId);
        }
        return item;
    }

    private AiJobResponse toResponse(
            AiJobEntity job,
            List<ImageAnalysisJobItemEntity> items,
            Map<UUID, BlogImageAnalysisEntity> analyses) {
        return new AiJobResponse(
                job.getId(),
                job.getBlogPostId(),
                job.getParentJobId(),
                job.getJobType(),
                job.getStatus(),
                job.getStage(),
                job.getTotalItems(),
                job.getProcessedItems(),
                job.getFailedItems(),
                job.getProgress(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                items.stream().map(item -> new ImageAnalysisItemResponse(
                        item.getAssetId(),
                        item.getDisplayOrder(),
                        item.getStatus(),
                        item.getErrorCode(),
                        analysisResponse(analyses.get(item.getAssetId())))).toList());
    }

    private ImageAnalysisResponse analysisResponse(BlogImageAnalysisEntity analysis) {
        return analysis == null ? null : new ImageAnalysisResponse(
                analysis.getAssetId(),
                analysis.getDisplayOrder(),
                analysis.getCategory(),
                analysis.getDescription(),
                analysis.getObjects(),
                analysis.getVisibleText(),
                analysis.getAnalyzedAt());
    }

    record AnalysisWorkItem(UUID assetId, int displayOrder, String analysisStorageKey) {
    }

    private record JobTarget(UUID assetId, int displayOrder) {
    }
}
