package com.mimir.blog;

import static com.mimir.blog.BlogApiModels.BlogAssetResponse;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.mimir.storage.StorageProvider;

@Service
public class BlogAssetService {

    static final int MAX_IMAGES_PER_POST = 20;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final BlogPostRepository postRepository;
    private final BlogAssetRepository assetRepository;
    private final StorageProvider storageProvider;
    private final Clock clock;
    private final long maxImageBytes;

    @Autowired
    BlogAssetService(
            BlogPostRepository postRepository,
            BlogAssetRepository assetRepository,
            StorageProvider storageProvider,
            @Value("${mimir.storage.max-image-bytes:15728640}") long maxImageBytes) {
        this(postRepository, assetRepository, storageProvider, Clock.systemUTC(), maxImageBytes);
    }

    BlogAssetService(
            BlogPostRepository postRepository,
            BlogAssetRepository assetRepository,
            StorageProvider storageProvider,
            Clock clock,
            long maxImageBytes) {
        this.postRepository = postRepository;
        this.assetRepository = assetRepository;
        this.storageProvider = storageProvider;
        this.clock = clock;
        this.maxImageBytes = maxImageBytes;
    }

    @Transactional(readOnly = true)
    public List<BlogAssetResponse> list(UUID postId) {
        requiredPost(postId);
        return assetRepository.findByBlogPostIdOrderByDisplayOrderAsc(postId).stream()
                .map(BlogAssetService::toResponse)
                .toList();
    }

    @Transactional
    public List<BlogAssetResponse> upload(UUID postId, List<? extends MultipartFile> files) {
        BlogPostEntity post = lockedPost(postId);
        if (files == null || files.isEmpty()) {
            throw new InvalidBlogAssetException("At least one image is required.");
        }
        long existingCount = assetRepository.countByBlogPostId(postId);
        if (existingCount + files.size() > MAX_IMAGES_PER_POST) {
            throw new BlogAssetLimitExceededException();
        }

        List<ValidatedImage> images = files.stream().map(this::validate).toList();
        Instant now = clock.instant();
        List<BlogAssetEntity> assets = new ArrayList<>();
        List<String> storedKeys = new ArrayList<>();
        try {
            for (int index = 0; index < images.size(); index++) {
                ValidatedImage image = images.get(index);
                UUID assetId = UUID.randomUUID();
                String storageKey = postId + "/" + assetId + "/original." + image.extension();
                storageProvider.store(storageKey, image.content());
                storedKeys.add(storageKey);
                assets.add(new BlogAssetEntity(
                        assetId,
                        postId,
                        Math.toIntExact(existingCount) + index,
                        image.filename(),
                        image.contentType(),
                        image.content().length,
                        storageKey,
                        now));
            }
            assetRepository.saveAllAndFlush(assets);
            cleanupOnRollback(storedKeys);
            post.touch(now);
        } catch (RuntimeException error) {
            storedKeys.forEach(this::deleteQuietly);
            throw error;
        }
        return assets.stream().map(BlogAssetService::toResponse).toList();
    }

    @Transactional
    public List<BlogAssetResponse> reorder(UUID postId, List<UUID> assetIds) {
        BlogPostEntity post = lockedPost(postId);
        List<UUID> existingIds = assetRepository.findByBlogPostIdOrderByDisplayOrderAsc(postId).stream()
                .map(BlogAssetEntity::getId)
                .toList();
        if (!isCompleteOrder(existingIds, assetIds)) {
            throw new InvalidBlogAssetException("Asset order must contain every current asset exactly once.");
        }
        post.touch(clock.instant());
        rewriteOrders(postId, assetIds);
        return assetRepository.findByBlogPostIdOrderByDisplayOrderAsc(postId).stream()
                .map(BlogAssetService::toResponse)
                .toList();
    }

    @Transactional
    public List<BlogAssetResponse> delete(UUID postId, UUID assetId) {
        BlogPostEntity post = lockedPost(postId);
        BlogAssetEntity asset = assetRepository.findByIdAndBlogPostId(assetId, postId)
                .orElseThrow(BlogAssetNotFoundException::new);
        assetRepository.delete(asset);
        assetRepository.flush();
        List<UUID> remainingIds = assetRepository.findByBlogPostIdOrderByDisplayOrderAsc(postId).stream()
                .map(BlogAssetEntity::getId)
                .toList();
        post.touch(clock.instant());
        rewriteOrders(postId, remainingIds);
        storageProvider.delete(asset.getStorageKey());
        return assetRepository.findByBlogPostIdOrderByDisplayOrderAsc(postId).stream()
                .map(BlogAssetService::toResponse)
                .toList();
    }

    static BlogAssetResponse toResponse(BlogAssetEntity asset) {
        return new BlogAssetResponse(
                asset.getId(),
                asset.getDisplayOrder(),
                asset.getOriginalFilename(),
                asset.getContentType(),
                asset.getByteSize(),
                asset.getCreatedAt());
    }

    private ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidBlogAssetException("Empty images are not allowed.");
        }
        if (file.getSize() > maxImageBytes) {
            throw new InvalidBlogAssetException("Image exceeds the configured size limit.");
        }
        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidBlogAssetException("Only JPEG, PNG, and WebP images are allowed.");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException error) {
            throw new InvalidBlogAssetException("Image content could not be read.");
        }
        if (content.length > maxImageBytes) {
            throw new InvalidBlogAssetException("Image exceeds the configured size limit.");
        }
        if (!matchesSignature(contentType, content)) {
            throw new InvalidBlogAssetException("Image content does not match its declared type.");
        }
        return new ValidatedImage(
                safeFilename(file.getOriginalFilename()),
                contentType,
                extension(contentType),
                content);
    }

    private void rewriteOrders(UUID postId, List<UUID> assetIds) {
        if (assetIds.isEmpty()) {
            return;
        }
        assetRepository.moveOrdersOutOfRange(postId);
        for (int index = 0; index < assetIds.size(); index++) {
            assetRepository.updateDisplayOrder(assetIds.get(index), index);
        }
    }

    private BlogPostEntity lockedPost(UUID postId) {
        return postRepository.findByIdForUpdate(postId).orElseThrow(() -> new BlogNotFoundException(postId));
    }

    private BlogPostEntity requiredPost(UUID postId) {
        return postRepository.findById(postId).orElseThrow(() -> new BlogNotFoundException(postId));
    }

    private boolean isCompleteOrder(List<UUID> existingIds, List<UUID> requestedIds) {
        return requestedIds != null
                && existingIds.size() == requestedIds.size()
                && new HashSet<>(requestedIds).size() == requestedIds.size()
                && new HashSet<>(existingIds).equals(new HashSet<>(requestedIds));
    }

    private boolean matchesSignature(String contentType, byte[] content) {
        return switch (contentType) {
            case "image/png" -> content.length >= 8
                    && content[0] == (byte) 0x89
                    && content[1] == 0x50
                    && content[2] == 0x4E
                    && content[3] == 0x47
                    && content[4] == 0x0D
                    && content[5] == 0x0A
                    && content[6] == 0x1A
                    && content[7] == 0x0A;
            case "image/jpeg" -> content.length >= 3
                    && content[0] == (byte) 0xFF
                    && content[1] == (byte) 0xD8
                    && content[2] == (byte) 0xFF;
            case "image/webp" -> content.length >= 12
                    && content[0] == 'R'
                    && content[1] == 'I'
                    && content[2] == 'F'
                    && content[3] == 'F'
                    && content[8] == 'W'
                    && content[9] == 'E'
                    && content[10] == 'B'
                    && content[11] == 'P';
            default -> false;
        };
    }

    private String safeFilename(String originalFilename) {
        String value = originalFilename == null ? "image" : originalFilename.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (value.isBlank()) {
            value = "image";
        }
        return value.length() <= 255 ? value : value.substring(value.length() - 255);
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalStateException("Unsupported validated content type.");
        };
    }

    private void deleteQuietly(String storageKey) {
        try {
            storageProvider.delete(storageKey);
        } catch (RuntimeException ignored) {
            // Preserve the original upload failure while leaving cleanup retryable.
        }
    }

    private void cleanupOnRollback(List<String> storageKeys) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storageKeys.forEach(BlogAssetService.this::deleteQuietly);
                }
            }
        });
    }

    private record ValidatedImage(String filename, String contentType, String extension, byte[] content) {
    }
}
