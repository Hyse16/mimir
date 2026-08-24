package com.mimir.blog;

import static com.mimir.blog.BlogApiModels.CreateBlogPostRequest;
import static com.mimir.blog.BlogApiModels.CreateDraftVersionRequest;
import static com.mimir.blog.BlogApiModels.UpdateBlogPostRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@Transactional
class BlogPostServiceIntegrationTest {

    private static final String ASSET_ROOT = System.getProperty("java.io.tmpdir") + "/mimir-assets-" + UUID.randomUUID();
    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.6-pg17")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PGVECTOR_IMAGE)
            .withDatabaseName("mimir_test")
            .withUsername("mimir")
            .withPassword("mimir_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("mimir.storage.local-root", () -> ASSET_ROOT);
    }

    @Autowired
    private BlogPostService service;

    @Autowired
    private BlogAssetService assetService;

    @Test
    void preservesEveryDraftVersionAndCanSelectAnOlderVersion() {
        var created = service.create(new CreateBlogPostRequest(
                "성수 카페 기록",
                "토요일에 친구와 방문",
                "첫 번째 본문",
                List.of("카페", "성수")));

        var revised = service.addVersion(created.id(), new CreateDraftVersionRequest(
                created.currentVersionId(),
                "성수 카페 방문기",
                "과장 표현을 줄인 두 번째 본문",
                List.of("#카페", "카페", "성수"),
                "일요일에 친구와 다시 방문",
                DraftSource.USER_EDIT));

        assertThat(revised.currentVersion().versionNumber()).isEqualTo(2);
        assertThat(revised.currentVersion().tags()).containsExactly("카페", "성수");
        assertThat(revised.visitContext()).isEqualTo("일요일에 친구와 다시 방문");
        assertThat(revised.versions()).hasSize(2);

        var restored = service.selectVersion(created.id(), created.currentVersionId());

        assertThat(restored.currentVersion().versionNumber()).isEqualTo(1);
        assertThat(restored.currentVersion().body()).isEqualTo("첫 번째 본문");
        assertThat(restored.versions()).hasSize(2);
    }

    @Test
    void rejectsARevisionBasedOnAStaleVersion() {
        var created = service.create(new CreateBlogPostRequest(
                "초안",
                "확인된 사실",
                "본문",
                List.of()));

        service.addVersion(created.id(), new CreateDraftVersionRequest(
                created.currentVersionId(),
                "수정 초안",
                "수정 본문",
                List.of(),
                null,
                DraftSource.USER_EDIT));

        assertThatThrownBy(() -> service.addVersion(created.id(), new CreateDraftVersionRequest(
                created.currentVersionId(),
                "충돌 초안",
                "충돌 본문",
                List.of(),
                "저장되면 안 되는 메모",
                DraftSource.USER_EDIT)))
                .isInstanceOf(StaleDraftVersionException.class);
        assertThat(service.detail(created.id()).visitContext()).isEqualTo("확인된 사실");
    }

    @Test
    void archivesWithoutDeletingVersions() {
        var created = service.create(new CreateBlogPostRequest(
                "보관 대상",
                "",
                "지워지면 안 되는 본문",
                List.of("기록")));

        var archived = service.archive(created.id());
        var listed = service.list(null, BlogPostStatus.ARCHIVED, 0, 20, "updatedAt", "desc");

        assertThat(archived.status()).isEqualTo(BlogPostStatus.ARCHIVED);
        assertThat(archived.versions()).hasSize(1);
        assertThat(listed.items()).extracting(BlogApiModels.BlogPostSummaryResponse::id)
                .contains(created.id());
    }

    @Test
    void duplicatesTheSelectedDraftAsAnIndependentPost() {
        var created = service.create(new CreateBlogPostRequest(
                "원본 기록",
                "검증된 사실 메모",
                "첫 번째 본문",
                List.of("기록")));
        var revised = service.addVersion(created.id(), new CreateDraftVersionRequest(
                created.currentVersionId(),
                "수정된 원본",
                "현재 선택된 본문",
                List.of("기록", "수정"),
                null,
                DraftSource.USER_EDIT));

        var duplicated = service.duplicate(created.id());

        assertThat(duplicated.id()).isNotEqualTo(created.id());
        assertThat(duplicated.title()).isEqualTo("수정된 원본 (복사본)");
        assertThat(duplicated.status()).isEqualTo(BlogPostStatus.DRAFT);
        assertThat(duplicated.visitContext()).isEqualTo("검증된 사실 메모");
        assertThat(duplicated.currentVersion().versionNumber()).isEqualTo(1);
        assertThat(duplicated.currentVersion().body()).isEqualTo(revised.currentVersion().body());
        assertThat(duplicated.currentVersion().tags()).containsExactly("기록", "수정");
        assertThat(duplicated.versions()).hasSize(1);
    }

    @Test
    void updatesLifecycleStatusWithoutCreatingAnotherVersion() {
        var created = service.create(new CreateBlogPostRequest(
                "검토할 글",
                "사실 메모",
                "검토 전 본문",
                List.of("검토")));

        var ready = service.update(created.id(), new UpdateBlogPostRequest(null, BlogPostStatus.READY, null));

        assertThat(ready.status()).isEqualTo(BlogPostStatus.READY);
        assertThat(ready.currentVersionId()).isEqualTo(created.currentVersionId());
        assertThat(ready.versions()).hasSize(1);
    }

    @Test
    void supportsZeroOneThreeTenAndTwentyOrderedImages() {
        for (int count : List.of(0, 1, 3, 10, 20)) {
            var post = createPost("이미지 " + count + "장");

            if (count > 0) {
                assetService.upload(post.id(), images(count));
            }

            var assets = assetService.list(post.id());
            assertThat(assets).hasSize(count);
            assertThat(service.detail(post.id()).assets()).hasSize(count);
            assertThat(assets).extracting(BlogApiModels.BlogAssetResponse::displayOrder)
                    .containsExactlyElementsOf(IntStream.range(0, count).boxed().toList());
        }
    }

    @Test
    void rejectsTheTwentyFirstImageWithoutChangingStoredAssets() {
        var post = createPost("최대 이미지");
        assetService.upload(post.id(), images(20));

        assertThatThrownBy(() -> assetService.upload(post.id(), images(1)))
                .isInstanceOf(BlogAssetLimitExceededException.class);
        assertThat(assetService.list(post.id())).hasSize(20);
    }

    @Test
    void requiresACompleteUniqueAssetOrder() {
        var post = createPost("순서 변경");
        var uploaded = assetService.upload(post.id(), images(3));
        var reversed = uploaded.reversed().stream().map(BlogApiModels.BlogAssetResponse::id).toList();

        var reordered = assetService.reorder(post.id(), reversed);

        assertThat(reordered).extracting(BlogApiModels.BlogAssetResponse::id)
                .containsExactlyElementsOf(reversed);
        assertThatThrownBy(() -> assetService.reorder(post.id(), List.of(reversed.getFirst(), reversed.getFirst())))
                .isInstanceOf(InvalidBlogAssetException.class);
    }

    @Test
    void deletesAnAssetAndCompactsTheRemainingOrder() {
        var post = createPost("이미지 삭제");
        var uploaded = assetService.upload(post.id(), images(3));

        var remaining = assetService.delete(post.id(), uploaded.get(1).id());

        assertThat(remaining).extracting(BlogApiModels.BlogAssetResponse::id)
                .containsExactly(uploaded.getFirst().id(), uploaded.getLast().id());
        assertThat(remaining).extracting(BlogApiModels.BlogAssetResponse::displayOrder)
                .containsExactly(0, 1);
    }

    @Test
    void sanitizesOriginalNamesAndRejectsSpoofedImageContent() {
        var post = createPost("안전한 업로드");
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01};
        var safe = new MockMultipartFile("files", "../../private/photo.png", "image/png", png);
        var spoofed = new MockMultipartFile("files", "photo.png", "image/png", "not-an-image".getBytes());

        assertThat(assetService.upload(post.id(), List.of(safe)).getFirst().originalFilename())
                .isEqualTo("photo.png");
        assertThatThrownBy(() -> assetService.upload(post.id(), List.of(spoofed)))
                .isInstanceOf(InvalidBlogAssetException.class);
        assertThat(assetService.list(post.id())).hasSize(1);
    }

    private BlogApiModels.BlogPostDetailResponse createPost(String title) {
        return service.create(new CreateBlogPostRequest(title, "", "", List.of()));
    }

    private List<MockMultipartFile> images(int count) {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01};
        return IntStream.range(0, count)
                .mapToObj(index -> new MockMultipartFile(
                        "files",
                        "image-" + index + ".png",
                        "image/png",
                        png))
                .toList();
    }
}
