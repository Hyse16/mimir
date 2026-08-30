package com.mimir.blog;

import static com.mimir.blog.BlogApiModels.CreateBlogPostRequest;
import static com.mimir.blog.BlogApiModels.CreateDraftVersionRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.mimir.ai.TextGenerationGateway;
import com.mimir.ai.VisionAnalysisGateway;

@Testcontainers
@SpringBootTest
@Import(DraftGenerationJobIntegrationTest.AiTestConfiguration.class)
class DraftGenerationJobIntegrationTest {

    private static final String ASSET_ROOT = System.getProperty("java.io.tmpdir")
            + "/mimir-draft-generation-assets-" + UUID.randomUUID();
    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.6-pg17")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PGVECTOR_IMAGE)
            .withDatabaseName("mimir_draft_generation_test")
            .withUsername("mimir")
            .withPassword("mimir_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("mimir.storage.local-root", () -> ASSET_ROOT);
        registry.add("mimir.image.optimized-max-dimension", () -> 4);
        registry.add("mimir.image.analysis-max-dimension", () -> 2);
    }

    @Autowired
    private BlogPostService blogService;

    @Autowired
    private BlogAssetService assetService;

    @Autowired
    private ImageAnalysisJobService imageJobService;

    @Autowired
    private ImageAnalysisJobRunner imageJobRunner;

    @Autowired
    private DraftGenerationJobService draftJobService;

    @Autowired
    private DraftGenerationJobRunner draftJobRunner;

    @Autowired
    private FakeTextGenerationGateway textGateway;

    @BeforeEach
    void resetGateway() {
        textGateway.reset();
    }

    @Test
    void createsAndSelectsAnAiGeneratedVersionFromGroundedContext() {
        var post = analyzedPost("토요일에 친구와 방문. 치즈케이크가 맛있었음.", 2);
        UUID jobId = draftJobService.create(post.id(), post.currentVersionId(), "편안한 존댓말로 작성");

        draftJobRunner.run(jobId);

        var job = imageJobService.detail(jobId);
        var generated = blogService.detail(post.id());
        assertThat(job.jobType()).isEqualTo(AiJobType.BLOG_DRAFT_GENERATION);
        assertThat(job.status()).isEqualTo(AiJobStatus.COMPLETED);
        assertThat(job.baseVersionId()).isEqualTo(post.currentVersionId());
        assertThat(job.resultVersionId()).isEqualTo(generated.currentVersionId());
        assertThat(job.items()).isEmpty();
        assertThat(generated.status()).isEqualTo(BlogPostStatus.REVIEW_REQUIRED);
        assertThat(generated.currentVersion().source()).isEqualTo(DraftSource.AI_GENERATED);
        assertThat(generated.currentVersion().versionNumber()).isEqualTo(2);
        assertThat(generated.currentVersion().body()).contains("{{IMAGE:1}}", "{{IMAGE:2}}");
        assertThat(generated.currentVersion().tags()).containsExactly("카페", "성수");
        assertThat(imageJobService.eventsAfter(jobId, 0)).extracting(event -> event.stage())
                .containsExactly(
                        AiJobStage.QUEUED,
                        AiJobStage.CONTEXT_ASSEMBLY,
                        AiJobStage.DRAFT_GENERATION,
                        AiJobStage.COMPLETE);
    }

    @Test
    void requiresSuccessfulAnalysisForEveryCurrentImage() {
        var post = blogService.create(new CreateBlogPostRequest("미분석", "확인된 사실", "", List.of()));
        assetService.upload(post.id(), images(1));

        assertThatThrownBy(() -> draftJobService.create(
                post.id(), post.currentVersionId(), "초안을 작성해줘"))
                .isInstanceOf(InvalidAiJobOperationException.class)
                .hasMessageContaining("Every current image");
    }

    @Test
    void discardsGeneratedOutputWhenTheSelectedVersionChangesDuringInference() {
        var post = analyzedPost("확인된 사실", 1);
        UUID jobId = draftJobService.create(post.id(), post.currentVersionId(), "간결하게 작성");
        textGateway.afterNextGeneration(() -> blogService.addVersion(post.id(), new CreateDraftVersionRequest(
                post.currentVersionId(),
                "사용자 수정",
                "생성 중 사용자가 저장한 본문",
                List.of("사용자"),
                null)));

        draftJobRunner.run(jobId);

        var job = imageJobService.detail(jobId);
        var detail = blogService.detail(post.id());
        assertThat(job.status()).isEqualTo(AiJobStatus.FAILED);
        assertThat(job.errorCode()).isEqualTo("STALE_BASE_VERSION");
        assertThat(job.resultVersionId()).isNull();
        assertThat(detail.versions()).hasSize(2);
        assertThat(detail.currentVersion().source()).isEqualTo(DraftSource.USER_EDIT);
        assertThat(detail.currentVersion().title()).isEqualTo("사용자 수정");
    }

    @Test
    void cancellationAfterInferenceDoesNotPersistTheGeneratedDraft() {
        var post = analyzedPost("확인된 사실", 1);
        UUID jobId = draftJobService.create(post.id(), post.currentVersionId(), "간결하게 작성");
        textGateway.afterNextGeneration(() -> imageJobService.requestCancellation(jobId));

        draftJobRunner.run(jobId);

        var job = imageJobService.detail(jobId);
        var detail = blogService.detail(post.id());
        assertThat(job.status()).isEqualTo(AiJobStatus.CANCELLED);
        assertThat(job.resultVersionId()).isNull();
        assertThat(detail.versions()).hasSize(1);
        assertThat(detail.status()).isEqualTo(BlogPostStatus.REVIEW_REQUIRED);
    }

    @Test
    void providerFailureLeavesTheExistingDraftSelected() {
        var post = analyzedPost("확인된 사실", 1);
        UUID jobId = draftJobService.create(post.id(), post.currentVersionId(), "간결하게 작성");
        textGateway.failNextGeneration();

        draftJobRunner.run(jobId);

        var job = imageJobService.detail(jobId);
        var detail = blogService.detail(post.id());
        assertThat(job.status()).isEqualTo(AiJobStatus.FAILED);
        assertThat(job.errorCode()).isEqualTo("TEXT_GENERATION_FAILED");
        assertThat(job.resultVersionId()).isNull();
        assertThat(detail.versions()).hasSize(1);
        assertThat(detail.currentVersionId()).isEqualTo(post.currentVersionId());
        assertThat(detail.status()).isEqualTo(BlogPostStatus.REVIEW_REQUIRED);
    }

    @Test
    void listsRevisionTurnsNewestFirstWithTheirOutcomes() {
        var post = analyzedPost("확인된 사실", 1);
        UUID completedJobId = draftJobService.create(
                post.id(), post.currentVersionId(), "첫 번째 수정 요청");
        draftJobRunner.run(completedJobId);
        var generated = blogService.detail(post.id());
        textGateway.failNextGeneration();
        UUID failedJobId = draftJobService.create(
                post.id(), generated.currentVersionId(), "두 번째 수정 요청");
        draftJobRunner.run(failedJobId);

        var history = draftJobService.history(post.id(), 0, 20);

        assertThat(history.totalItems()).isEqualTo(2);
        assertThat(history.items()).extracting(DraftGenerationApiModels.DraftRevisionTurnResponse::id)
                .containsExactly(failedJobId, completedJobId);
        assertThat(history.items().getFirst().revisionInstruction()).isEqualTo("두 번째 수정 요청");
        assertThat(history.items().getFirst().status()).isEqualTo(AiJobStatus.FAILED);
        assertThat(history.items().getFirst().errorCode()).isEqualTo("TEXT_GENERATION_FAILED");
        assertThat(history.items().get(1).revisionInstruction()).isEqualTo("첫 번째 수정 요청");
        assertThat(history.items().get(1).status()).isEqualTo(AiJobStatus.COMPLETED);
        assertThat(history.items().get(1).resultVersionId()).isEqualTo(generated.currentVersionId());
        assertThat(history.items()).extracting(DraftGenerationApiModels.DraftRevisionTurnResponse::target)
                .containsOnly(DraftGenerationTarget.FULL);
    }

    @Test
    void titleTargetPersistsOnlyTheGeneratedTitle() {
        var post = analyzedPost("확인된 사실", 1);
        UUID jobId = draftJobService.create(
                post.id(),
                post.currentVersionId(),
                "제목만 더 간결하게",
                DraftGenerationTarget.TITLE);

        draftJobRunner.run(jobId);

        var generated = blogService.detail(post.id());
        assertThat(generated.currentVersion().title()).isEqualTo("AI 성수 카페 기록");
        assertThat(generated.currentVersion().body()).isEqualTo(post.currentVersion().body());
        assertThat(generated.currentVersion().tags()).isEqualTo(post.currentVersion().tags());
        assertThat(draftJobService.history(post.id(), 0, 20).items().getFirst().target())
                .isEqualTo(DraftGenerationTarget.TITLE);
    }

    @Test
    void bodyTargetPersistsOnlyTheGeneratedBody() {
        var post = analyzedPost("확인된 사실", 1);
        UUID jobId = draftJobService.create(
                post.id(),
                post.currentVersionId(),
                "본문만 더 간결하게",
                DraftGenerationTarget.BODY);

        draftJobRunner.run(jobId);

        var generated = blogService.detail(post.id());
        assertThat(generated.currentVersion().title()).isEqualTo(post.currentVersion().title());
        assertThat(generated.currentVersion().body()).contains("{{IMAGE:1}}");
        assertThat(generated.currentVersion().body()).isNotEqualTo(post.currentVersion().body());
        assertThat(generated.currentVersion().tags()).isEqualTo(post.currentVersion().tags());
        assertThat(draftJobService.history(post.id(), 0, 20).items().getFirst().target())
                .isEqualTo(DraftGenerationTarget.BODY);
    }

    @Test
    void tagsTargetPersistsOnlyTheGeneratedTags() {
        var post = analyzedPost("확인된 사실", 1);
        UUID jobId = draftJobService.create(
                post.id(),
                post.currentVersionId(),
                "태그만 정리해줘",
                DraftGenerationTarget.TAGS);

        draftJobRunner.run(jobId);

        var generated = blogService.detail(post.id());
        assertThat(generated.currentVersion().title()).isEqualTo(post.currentVersion().title());
        assertThat(generated.currentVersion().body()).isEqualTo(post.currentVersion().body());
        assertThat(generated.currentVersion().tags()).containsExactly("카페", "성수");
        assertThat(draftJobService.history(post.id(), 0, 20).items().getFirst().target())
                .isEqualTo(DraftGenerationTarget.TAGS);
    }

    private BlogApiModels.BlogPostDetailResponse analyzedPost(String context, int imageCount) {
        var post = blogService.create(new CreateBlogPostRequest("성수 카페", context, "기존 본문", List.of("성수")));
        assetService.upload(post.id(), images(imageCount));
        UUID analysisJobId = imageJobService.create(post.id());
        imageJobRunner.run(analysisJobId);
        assertThat(imageJobService.detail(analysisJobId).status()).isEqualTo(AiJobStatus.COMPLETED);
        return blogService.detail(post.id());
    }

    private List<MockMultipartFile> images(int count) {
        byte[] png = pngImage();
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new MockMultipartFile(
                        "files", "image-" + index + ".png", "image/png", png))
                .toList();
    }

    private byte[] pngImage() {
        BufferedImage image = new BufferedImage(8, 4, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, new Color(x * 20, y * 40, 120).getRGB());
            }
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    @TestConfiguration
    static class AiTestConfiguration {

        @Bean
        @Primary
        VisionAnalysisGateway fakeVisionGateway() {
            return images -> images.stream()
                    .map(image -> new VisionAnalysisGateway.VisionAnalysis(
                            image.assetId(),
                            image.displayOrder(),
                            "장면",
                            "확인된 이미지 " + image.displayOrder(),
                            List.of("객체"),
                            null))
                    .toList();
        }

        @Bean
        @Primary
        FakeTextGenerationGateway fakeTextGenerationGateway() {
            return new FakeTextGenerationGateway();
        }
    }

    static class FakeTextGenerationGateway implements TextGenerationGateway {

        private volatile Runnable callback;
        private volatile boolean fail;

        void reset() {
            callback = null;
            fail = false;
        }

        void afterNextGeneration(Runnable nextCallback) {
            callback = nextCallback;
        }

        void failNextGeneration() {
            fail = true;
        }

        @Override
        public GeneratedDraft generate(DraftGenerationRequest request) {
            if (fail) {
                fail = false;
                throw new IllegalStateException("simulated local text provider failure");
            }
            StringBuilder body = new StringBuilder();
            for (int index = 1; index <= request.imageFacts().size(); index++) {
                body.append("{{IMAGE:").append(index).append("}}\n확인된 이미지입니다.\n");
            }
            Runnable nextCallback = callback;
            callback = null;
            if (nextCallback != null) {
                nextCallback.run();
            }
            return new GeneratedDraft("AI 성수 카페 기록", body.toString(), List.of("#카페", "성수"));
        }
    }
}
