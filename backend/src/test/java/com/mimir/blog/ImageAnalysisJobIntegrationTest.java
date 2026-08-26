package com.mimir.blog;

import static com.mimir.blog.BlogApiModels.CreateBlogPostRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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

import com.mimir.ai.VisionAnalysisGateway;

@Testcontainers
@SpringBootTest
@Import(ImageAnalysisJobIntegrationTest.VisionTestConfiguration.class)
class ImageAnalysisJobIntegrationTest {

    private static final String ASSET_ROOT = System.getProperty("java.io.tmpdir")
            + "/mimir-analysis-assets-" + UUID.randomUUID();
    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.6-pg17")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PGVECTOR_IMAGE)
            .withDatabaseName("mimir_analysis_test")
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
        registry.add("mimir.ai.vision-batch-size", () -> 3);
    }

    @Autowired
    private BlogPostService blogService;

    @Autowired
    private BlogAssetService assetService;

    @Autowired
    private ImageAnalysisJobService jobService;

    @Autowired
    private ImageAnalysisJobRunner runner;

    @Autowired
    private FakeVisionGateway gateway;

    @BeforeEach
    void resetGateway() {
        gateway.reset();
    }

    @Test
    void runsSequentialBatchesPersistsPartialFailureAndRetriesOnlyFailedItems() {
        var post = blogService.create(new CreateBlogPostRequest("Vision 배치", "확인된 사실", "", List.of()));
        assetService.upload(post.id(), images(5));
        gateway.failNextCall();

        UUID jobId = jobService.create(post.id());
        assertThatThrownBy(() -> jobService.create(post.id()))
                .isInstanceOf(InvalidAiJobOperationException.class)
                .hasMessageContaining("already active");
        runner.run(jobId);

        var partial = jobService.detail(jobId);
        assertThat(gateway.batchSizes()).containsExactly(3, 2);
        assertThat(partial.status()).isEqualTo(AiJobStatus.PARTIAL_FAILED);
        assertThat(partial.processedItems()).isEqualTo(2);
        assertThat(partial.failedItems()).isEqualTo(3);
        assertThat(partial.progress()).isEqualTo(100);
        assertThat(partial.items().subList(0, 3))
                .allMatch(item -> item.status() == ImageAnalysisItemStatus.FAILED
                        && "VISION_PROVIDER_FAILED".equals(item.errorCode()));
        assertThat(partial.items().subList(3, 5))
                .allMatch(item -> item.status() == ImageAnalysisItemStatus.SUCCEEDED
                        && item.analysis() != null
                        && item.analysis().description().startsWith("visible image"));

        gateway.clearBatchSizes();
        UUID retryJobId = jobService.retryFailed(jobId);
        runner.run(retryJobId);

        var retry = jobService.detail(retryJobId);
        assertThat(gateway.batchSizes()).containsExactly(3);
        assertThat(retry.parentJobId()).isEqualTo(jobId);
        assertThat(retry.totalItems()).isEqualTo(3);
        assertThat(retry.status()).isEqualTo(AiJobStatus.COMPLETED);
        assertThat(retry.items()).allMatch(item -> item.status() == ImageAnalysisItemStatus.SUCCEEDED);
        assertThat(jobService.detail(jobId).items().subList(0, 3))
                .allMatch(item -> item.status() == ImageAnalysisItemStatus.FAILED && item.analysis() == null);

        var events = jobService.eventsAfter(jobId, 0);
        assertThat(events).extracting(event -> event.status())
                .containsExactly(
                        AiJobStatus.WAITING,
                        AiJobStatus.RUNNING,
                        AiJobStatus.RUNNING,
                        AiJobStatus.PARTIAL_FAILED);
        long resumeAfter = events.get(1).eventId();
        assertThat(jobService.eventsAfter(jobId, resumeAfter))
                .extracting(event -> event.eventId())
                .containsExactlyElementsOf(events.subList(2, events.size()).stream()
                        .map(event -> event.eventId())
                        .toList());
    }

    @Test
    void cancelsRemainingItemsAfterTheCurrentBatchCompletes() {
        var post = blogService.create(new CreateBlogPostRequest("취소 경계", "확인된 사실", "", List.of()));
        assetService.upload(post.id(), images(5));
        UUID jobId = jobService.create(post.id());
        gateway.afterNextCall(() -> jobService.requestCancellation(jobId));

        runner.run(jobId);

        var cancelled = jobService.detail(jobId);
        assertThat(gateway.batchSizes()).containsExactly(3);
        assertThat(cancelled.status()).isEqualTo(AiJobStatus.CANCELLED);
        assertThat(cancelled.stage()).isEqualTo(AiJobStage.COMPLETE);
        assertThat(cancelled.processedItems()).isEqualTo(3);
        assertThat(cancelled.failedItems()).isZero();
        assertThat(cancelled.progress()).isEqualTo(60);
        assertThat(cancelled.cancelRequestedAt()).isNotNull();
        assertThat(cancelled.items().subList(0, 3))
                .allMatch(item -> item.status() == ImageAnalysisItemStatus.SUCCEEDED);
        assertThat(cancelled.items().subList(3, 5))
                .allMatch(item -> item.status() == ImageAnalysisItemStatus.CANCELLED);
        assertThat(jobService.eventsAfter(jobId, 0)).extracting(event -> event.status())
                .containsExactly(
                        AiJobStatus.WAITING,
                        AiJobStatus.RUNNING,
                        AiJobStatus.CANCEL_REQUESTED,
                        AiJobStatus.CANCEL_REQUESTED,
                        AiJobStatus.CANCELLED);
    }

    @Test
    void cancelsAWaitingJobIdempotentlyBeforeTheRunnerStarts() {
        var post = blogService.create(new CreateBlogPostRequest("대기 취소", "확인된 사실", "", List.of()));
        assetService.upload(post.id(), images(1));
        UUID jobId = jobService.create(post.id());

        jobService.requestCancellation(jobId);
        jobService.requestCancellation(jobId);
        runner.run(jobId);

        var cancelled = jobService.detail(jobId);
        assertThat(cancelled.status()).isEqualTo(AiJobStatus.CANCELLED);
        assertThat(cancelled.items()).allMatch(item -> item.status() == ImageAnalysisItemStatus.CANCELLED);
        assertThat(gateway.batchSizes()).isEmpty();
        assertThat(jobService.eventsAfter(jobId, 0)).extracting(event -> event.status())
                .containsExactly(AiJobStatus.WAITING, AiJobStatus.CANCEL_REQUESTED, AiJobStatus.CANCELLED);
    }

    private List<MockMultipartFile> images(int count) {
        byte[] png = pngImage();
        return IntStream.range(0, count)
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
    static class VisionTestConfiguration {

        @Bean
        @Primary
        FakeVisionGateway fakeVisionGateway() {
            return new FakeVisionGateway();
        }
    }

    static class FakeVisionGateway implements VisionAnalysisGateway {

        private final AtomicInteger calls = new AtomicInteger();
        private final List<Integer> batchSizes = new ArrayList<>();
        private volatile int failingCall = -1;
        private volatile Runnable afterNextCall;

        void reset() {
            calls.set(0);
            batchSizes.clear();
            failingCall = -1;
            afterNextCall = null;
        }

        void failNextCall() {
            failingCall = calls.get() + 1;
        }

        void clearBatchSizes() {
            batchSizes.clear();
        }

        void afterNextCall(Runnable callback) {
            afterNextCall = callback;
        }

        List<Integer> batchSizes() {
            return List.copyOf(batchSizes);
        }

        @Override
        public List<VisionAnalysis> analyze(List<VisionImage> images) {
            int call = calls.incrementAndGet();
            batchSizes.add(images.size());
            if (call == failingCall) {
                throw new IllegalStateException("simulated local provider failure");
            }
            List<VisionAnalysis> results = images.stream()
                    .map(image -> new VisionAnalysis(
                            image.assetId(),
                            image.displayOrder(),
                            "scene",
                            "visible image " + image.displayOrder(),
                            List.of("object"),
                            null))
                    .toList();
            Runnable callback = afterNextCall;
            afterNextCall = null;
            if (callback != null) {
                callback.run();
            }
            return results;
        }
    }
}
