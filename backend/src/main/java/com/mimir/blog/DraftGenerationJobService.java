package com.mimir.blog;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mimir.ai.GeneratedDraftValidator;
import com.mimir.ai.TextGenerationGateway.DraftGenerationRequest;
import com.mimir.ai.TextGenerationGateway.GeneratedDraft;
import com.mimir.ai.TextGenerationGateway.ImageFact;

@Service
class DraftGenerationJobService {

    private final BlogPostRepository postRepository;
    private final BlogContextRepository contextRepository;
    private final BlogDraftVersionRepository versionRepository;
    private final BlogAssetRepository assetRepository;
    private final BlogImageAnalysisRepository analysisRepository;
    private final AiJobRepository jobRepository;
    private final AiJobEventRepository eventRepository;
    private final GeneratedDraftValidator validator;
    private final Clock clock;

    @Autowired
    DraftGenerationJobService(
            BlogPostRepository postRepository,
            BlogContextRepository contextRepository,
            BlogDraftVersionRepository versionRepository,
            BlogAssetRepository assetRepository,
            BlogImageAnalysisRepository analysisRepository,
            AiJobRepository jobRepository,
            AiJobEventRepository eventRepository,
            GeneratedDraftValidator validator) {
        this(postRepository, contextRepository, versionRepository, assetRepository, analysisRepository,
                jobRepository, eventRepository, validator, Clock.systemUTC());
    }

    DraftGenerationJobService(
            BlogPostRepository postRepository,
            BlogContextRepository contextRepository,
            BlogDraftVersionRepository versionRepository,
            BlogAssetRepository assetRepository,
            BlogImageAnalysisRepository analysisRepository,
            AiJobRepository jobRepository,
            AiJobEventRepository eventRepository,
            GeneratedDraftValidator validator,
            Clock clock) {
        this.postRepository = postRepository;
        this.contextRepository = contextRepository;
        this.versionRepository = versionRepository;
        this.assetRepository = assetRepository;
        this.analysisRepository = analysisRepository;
        this.jobRepository = jobRepository;
        this.eventRepository = eventRepository;
        this.validator = validator;
        this.clock = clock;
    }

    @Transactional
    public UUID create(UUID postId, UUID baseVersionId, String revisionInstruction) {
        return create(postId, baseVersionId, revisionInstruction, DraftGenerationTarget.FULL);
    }

    @Transactional
    public UUID create(
            UUID postId,
            UUID baseVersionId,
            String revisionInstruction,
            DraftGenerationTarget target) {
        if (baseVersionId == null || revisionInstruction == null || revisionInstruction.isBlank()) {
            throw new IllegalArgumentException("Base version and revision instruction are required.");
        }
        if (target == null) {
            throw new IllegalArgumentException("Draft generation target is required.");
        }
        BlogPostEntity post = requiredPostForUpdate(postId);
        if (post.getStatus() == BlogPostStatus.ARCHIVED) {
            throw new InvalidAiJobOperationException("Archived posts cannot start draft generation.");
        }
        if (!baseVersionId.equals(post.getCurrentVersionId())) {
            throw new StaleDraftVersionException();
        }
        requireNoActiveJob(postId);
        requireImageFacts(postId);
        Instant now = clock.instant();
        AiJobEntity job = jobRepository.save(new AiJobEntity(
                UUID.randomUUID(), postId, baseVersionId, revisionInstruction.strip(), target, now));
        recordEvent(job, now);
        return job.getId();
    }

    @Transactional(readOnly = true)
    public DraftGenerationApiModels.DraftRevisionTurnPageResponse history(UUID postId, int page, int size) {
        if (!postRepository.existsById(postId)) {
            throw new BlogNotFoundException(postId);
        }
        var turns = jobRepository.findByBlogPostIdAndJobTypeOrderByCreatedAtDesc(
                postId,
                AiJobType.BLOG_DRAFT_GENERATION,
                PageRequest.of(page, size));
        return new DraftGenerationApiModels.DraftRevisionTurnPageResponse(
                turns.getContent().stream().map(this::turnResponse).toList(),
                turns.getNumber(),
                turns.getSize(),
                turns.getTotalElements(),
                turns.getTotalPages());
    }

    @Transactional
    public DraftGenerationRequest start(UUID jobId) {
        AiJobEntity job = requiredJobForUpdate(jobId);
        if (job.getStatus() == AiJobStatus.CANCELLED) {
            return null;
        }
        requireDraftJob(job);
        if (job.getStatus() != AiJobStatus.WAITING) {
            throw new InvalidAiJobOperationException("Only waiting draft generation jobs can start.");
        }
        Instant now = clock.instant();
        BlogPostEntity post = requiredPostForUpdate(job.getBlogPostId());
        if (!job.getBaseVersionId().equals(post.getCurrentVersionId())) {
            job.fail("STALE_BASE_VERSION", now);
            recordEvent(job, now);
            return null;
        }
        BlogDraftVersionEntity baseVersion = versionRepository.findByIdAndBlogPostId(
                        job.getBaseVersionId(), job.getBlogPostId())
                .orElseThrow(() -> new InvalidAiJobOperationException("The base draft version is unavailable."));
        List<ImageFact> imageFacts;
        try {
            imageFacts = requireImageFacts(job.getBlogPostId());
        } catch (InvalidAiJobOperationException error) {
            job.fail("IMAGE_ANALYSIS_REQUIRED", now);
            recordEvent(job, now);
            return null;
        }
        BlogContextEntity context = contextRepository.findById(job.getBlogPostId())
                .orElseThrow(() -> new IllegalStateException("Blog context is missing."));
        post.startGeneration(now);
        job.start(AiJobStage.CONTEXT_ASSEMBLY, now);
        recordEvent(job, now);
        job.advanceStage(AiJobStage.DRAFT_GENERATION);
        recordEvent(job, now);
        return new DraftGenerationRequest(
                baseVersion.getTitle(),
                baseVersion.getBody(),
                baseVersion.getTags(),
                context.getVisitContext(),
                imageFacts,
                job.getRevisionInstruction(),
                job.getGenerationTarget().toGatewayTarget());
    }

    @Transactional
    public void complete(UUID jobId, GeneratedDraft draft) {
        AiJobEntity job = requiredJobForUpdate(jobId);
        requireDraftJob(job);
        Instant now = clock.instant();
        BlogPostEntity post = requiredPostForUpdate(job.getBlogPostId());
        if (job.getStatus() == AiJobStatus.CANCEL_REQUESTED) {
            job.cancel(now);
            post.failGeneration(now);
            recordEvent(job, now);
            return;
        }
        if (job.getStatus() != AiJobStatus.RUNNING) {
            return;
        }
        if (!job.getBaseVersionId().equals(post.getCurrentVersionId())) {
            job.fail("STALE_BASE_VERSION", now);
            post.failGeneration(now);
            recordEvent(job, now);
            return;
        }
        GeneratedDraft validated = validator.validate(generationRequest(job), draft);
        int nextVersion = versionRepository.findMaximumVersionNumber(job.getBlogPostId()) + 1;
        BlogDraftVersionEntity version = versionRepository.save(new BlogDraftVersionEntity(
                UUID.randomUUID(),
                job.getBlogPostId(),
                nextVersion,
                DraftSource.AI_GENERATED,
                validated.title(),
                validated.body(),
                validated.tags(),
                now));
        post.selectVersion(version.getId(), version.getTitle(), now);
        post.finishGeneration(now);
        job.completeDraft(version.getId(), now);
        recordEvent(job, now);
    }

    @Transactional
    public void fail(UUID jobId, String errorCode) {
        AiJobEntity job = requiredJobForUpdate(jobId);
        requireDraftJob(job);
        if (job.getStatus() == AiJobStatus.CANCEL_REQUESTED) {
            Instant now = clock.instant();
            job.cancel(now);
            requiredPostForUpdate(job.getBlogPostId()).failGeneration(now);
            recordEvent(job, now);
            return;
        }
        if (job.getStatus() != AiJobStatus.RUNNING) {
            return;
        }
        Instant now = clock.instant();
        job.fail(errorCode, now);
        requiredPostForUpdate(job.getBlogPostId()).failGeneration(now);
        recordEvent(job, now);
    }

    private List<ImageFact> requireImageFacts(UUID postId) {
        return assetRepository.findByBlogPostIdOrderByDisplayOrderAsc(postId).stream()
                .map(asset -> analysisRepository.findFirstByAssetIdOrderByAnalyzedAtDesc(asset.getId())
                        .map(analysis -> new ImageFact(
                                asset.getDisplayOrder(),
                                analysis.getCategory(),
                                analysis.getDescription(),
                                analysis.getObjects(),
                                analysis.getVisibleText()))
                        .orElseThrow(() -> new InvalidAiJobOperationException(
                                "Every current image must have a successful analysis before draft generation.")))
                .toList();
    }

    private DraftGenerationRequest generationRequest(AiJobEntity job) {
        BlogDraftVersionEntity baseVersion = versionRepository.findByIdAndBlogPostId(
                        job.getBaseVersionId(), job.getBlogPostId())
                .orElseThrow(() -> new InvalidAiJobOperationException("The base draft version is unavailable."));
        BlogContextEntity context = contextRepository.findById(job.getBlogPostId())
                .orElseThrow(() -> new IllegalStateException("Blog context is missing."));
        return new DraftGenerationRequest(
                baseVersion.getTitle(),
                baseVersion.getBody(),
                baseVersion.getTags(),
                context.getVisitContext(),
                requireImageFacts(job.getBlogPostId()),
                job.getRevisionInstruction(),
                job.getGenerationTarget().toGatewayTarget());
    }

    private void requireNoActiveJob(UUID postId) {
        if (jobRepository.existsByBlogPostIdAndStatusIn(
                postId,
                List.of(AiJobStatus.WAITING, AiJobStatus.RUNNING, AiJobStatus.CANCEL_REQUESTED))) {
            throw new InvalidAiJobOperationException("An AI job is already active for this post.");
        }
    }

    private void requireDraftJob(AiJobEntity job) {
        if (job.getJobType() != AiJobType.BLOG_DRAFT_GENERATION) {
            throw new InvalidAiJobOperationException("The job is not a draft generation job.");
        }
    }

    private BlogPostEntity requiredPostForUpdate(UUID postId) {
        return postRepository.findByIdForUpdate(postId).orElseThrow(() -> new BlogNotFoundException(postId));
    }

    private AiJobEntity requiredJobForUpdate(UUID jobId) {
        return jobRepository.findByIdForUpdate(jobId).orElseThrow(() -> new AiJobNotFoundException(jobId));
    }

    private void recordEvent(AiJobEntity job, Instant now) {
        eventRepository.save(new AiJobEventEntity(job, now));
    }

    private DraftGenerationApiModels.DraftRevisionTurnResponse turnResponse(AiJobEntity job) {
        return new DraftGenerationApiModels.DraftRevisionTurnResponse(
                job.getId(),
                job.getStatus(),
                job.getStage(),
                job.getBaseVersionId(),
                job.getResultVersionId(),
                job.getRevisionInstruction(),
                job.getGenerationTarget(),
                job.getErrorCode(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt());
    }
}
