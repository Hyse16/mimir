package com.mimir.blog;

import static com.mimir.blog.BlogApiModels.BlogPostDetailResponse;
import static com.mimir.blog.BlogApiModels.BlogPostSummaryResponse;
import static com.mimir.blog.BlogApiModels.CreateBlogPostRequest;
import static com.mimir.blog.BlogApiModels.CreateDraftVersionRequest;
import static com.mimir.blog.BlogApiModels.DraftVersionResponse;
import static com.mimir.blog.BlogApiModels.PageResponse;
import static com.mimir.blog.BlogApiModels.UpdateBlogPostRequest;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlogPostService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final String COPY_SUFFIX = " (복사본)";
    private static final List<String> ALLOWED_SORT_FIELDS = List.of("createdAt", "updatedAt", "title", "status");

    private final BlogPostRepository postRepository;
    private final BlogContextRepository contextRepository;
    private final BlogDraftVersionRepository versionRepository;
    private final Clock clock;

    @Autowired
    BlogPostService(
            BlogPostRepository postRepository,
            BlogContextRepository contextRepository,
            BlogDraftVersionRepository versionRepository) {
        this(postRepository, contextRepository, versionRepository, Clock.systemUTC());
    }

    BlogPostService(
            BlogPostRepository postRepository,
            BlogContextRepository contextRepository,
            BlogDraftVersionRepository versionRepository,
            Clock clock) {
        this.postRepository = postRepository;
        this.contextRepository = contextRepository;
        this.versionRepository = versionRepository;
        this.clock = clock;
    }

    @Transactional
    public BlogPostDetailResponse create(CreateBlogPostRequest request) {
        Instant now = clock.instant();
        UUID postId = UUID.randomUUID();
        String title = request.title().trim();
        BlogPostEntity post = postRepository.save(new BlogPostEntity(postId, title, now));
        contextRepository.save(new BlogContextEntity(postId, normalizedText(request.visitContext()), now));

        BlogDraftVersionEntity version = versionRepository.save(new BlogDraftVersionEntity(
                UUID.randomUUID(),
                postId,
                1,
                DraftSource.USER_EDIT,
                title,
                normalizedText(request.body()),
                normalizeTags(request.tags()),
                now));
        post.selectVersion(version.getId(), version.getTitle(), now);
        postRepository.save(post);
        return detail(postId);
    }

    @Transactional(readOnly = true)
    public PageResponse<BlogPostSummaryResponse> list(
            String query,
            BlogPostStatus status,
            int page,
            int size,
            String sort,
            String direction) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        String sortField = ALLOWED_SORT_FIELDS.contains(sort) ? sort : "updatedAt";
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String normalizedQuery = normalizedQuery(query);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(sortDirection, sortField));
        Page<BlogPostEntity> result;
        if (status != null && normalizedQuery != null) {
            result = postRepository.findByStatusAndTitleContainingIgnoreCase(status, normalizedQuery, pageable);
        } else if (status != null) {
            result = postRepository.findByStatus(status, pageable);
        } else if (normalizedQuery != null) {
            result = postRepository.findByTitleContainingIgnoreCase(normalizedQuery, pageable);
        } else {
            result = postRepository.findAll(pageable);
        }
        return new PageResponse<>(
                result.getContent().stream().map(this::summary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public BlogPostDetailResponse detail(UUID postId) {
        BlogPostEntity post = requiredPost(postId);
        BlogContextEntity context = contextRepository.findById(postId)
                .orElseThrow(() -> new IllegalStateException("Blog context is missing."));
        List<BlogDraftVersionEntity> versions = versionRepository.findByBlogPostIdOrderByVersionNumberDesc(postId);
        DraftVersionResponse currentVersion = versions.stream()
                .filter(version -> version.getId().equals(post.getCurrentVersionId()))
                .findFirst()
                .map(version -> versionResponse(version, true))
                .orElseThrow(() -> new IllegalStateException("Current blog draft version is missing."));
        return new BlogPostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getStatus(),
                context.getVisitContext(),
                post.getCurrentVersionId(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                currentVersion,
                versions.stream()
                        .map(version -> versionResponse(version, version.getId().equals(post.getCurrentVersionId())))
                        .toList());
    }

    @Transactional
    public BlogPostDetailResponse update(UUID postId, UpdateBlogPostRequest request) {
        if (request.title() == null && request.status() == null && request.visitContext() == null) {
            throw new IllegalArgumentException("At least one field must be provided.");
        }
        BlogPostEntity post = requiredPost(postId);
        BlogContextEntity context = contextRepository.findById(postId)
                .orElseThrow(() -> new IllegalStateException("Blog context is missing."));
        Instant now = clock.instant();
        String title = request.title() == null ? null : request.title().trim();
        post.updateMetadata(title, request.status(), now);
        if (request.visitContext() != null) {
            context.update(request.visitContext().trim(), now);
        }
        return detail(postId);
    }

    @Transactional
    public BlogPostDetailResponse addVersion(UUID postId, CreateDraftVersionRequest request) {
        BlogPostEntity post = requiredPost(postId);
        if (!request.baseVersionId().equals(post.getCurrentVersionId())) {
            throw new StaleDraftVersionException();
        }
        int nextVersion = versionRepository.findMaximumVersionNumber(postId) + 1;
        Instant now = clock.instant();
        BlogDraftVersionEntity version = versionRepository.save(new BlogDraftVersionEntity(
                UUID.randomUUID(),
                postId,
                nextVersion,
                request.source(),
                request.title().trim(),
                normalizedText(request.body()),
                normalizeTags(request.tags()),
                now));
        post.selectVersion(version.getId(), version.getTitle(), now);
        return detail(postId);
    }

    @Transactional
    public BlogPostDetailResponse selectVersion(UUID postId, UUID versionId) {
        BlogPostEntity post = requiredPost(postId);
        BlogDraftVersionEntity version = versionRepository.findByIdAndBlogPostId(versionId, postId)
                .orElseThrow(() -> new BlogNotFoundException(postId));
        post.selectVersion(version.getId(), version.getTitle(), clock.instant());
        return detail(postId);
    }

    @Transactional
    public BlogPostDetailResponse archive(UUID postId) {
        BlogPostEntity post = requiredPost(postId);
        post.archive(clock.instant());
        return detail(postId);
    }

    @Transactional
    public BlogPostDetailResponse duplicate(UUID postId) {
        BlogPostDetailResponse source = detail(postId);
        DraftVersionResponse selected = source.currentVersion();
        return create(new CreateBlogPostRequest(
                copyTitle(selected.title()),
                source.visitContext(),
                selected.body(),
                selected.tags()));
    }

    private BlogPostEntity requiredPost(UUID postId) {
        return postRepository.findById(postId).orElseThrow(() -> new BlogNotFoundException(postId));
    }

    private BlogPostSummaryResponse summary(BlogPostEntity post) {
        return new BlogPostSummaryResponse(
                post.getId(),
                post.getTitle(),
                post.getStatus(),
                post.getCurrentVersionId(),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }

    private DraftVersionResponse versionResponse(BlogDraftVersionEntity version, boolean selected) {
        return new DraftVersionResponse(
                version.getId(),
                version.getVersionNumber(),
                version.getSource(),
                version.getTitle(),
                version.getBody(),
                version.getTags(),
                version.getCreatedAt(),
                selected);
    }

    private static String normalizedText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizedQuery(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            String value = tag.trim();
            if (value.startsWith("#")) {
                value = value.substring(1);
            }
            if (!value.isBlank()) {
                normalized.add(value.toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(normalized);
    }

    private static String copyTitle(String title) {
        int sourceLength = Math.min(title.length(), MAX_TITLE_LENGTH - COPY_SUFFIX.length());
        return title.substring(0, sourceLength).stripTrailing() + COPY_SUFFIX;
    }
}
