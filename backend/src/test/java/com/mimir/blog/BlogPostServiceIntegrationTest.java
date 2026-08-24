package com.mimir.blog;

import static com.mimir.blog.BlogApiModels.CreateBlogPostRequest;
import static com.mimir.blog.BlogApiModels.CreateDraftVersionRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@Transactional
class BlogPostServiceIntegrationTest {

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
    }

    @Autowired
    private BlogPostService service;

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
                DraftSource.USER_EDIT));

        assertThat(revised.currentVersion().versionNumber()).isEqualTo(2);
        assertThat(revised.currentVersion().tags()).containsExactly("카페", "성수");
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
                DraftSource.USER_EDIT));

        assertThatThrownBy(() -> service.addVersion(created.id(), new CreateDraftVersionRequest(
                created.currentVersionId(),
                "충돌 초안",
                "충돌 본문",
                List.of(),
                DraftSource.USER_EDIT)))
                .isInstanceOf(StaleDraftVersionException.class);
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
}
