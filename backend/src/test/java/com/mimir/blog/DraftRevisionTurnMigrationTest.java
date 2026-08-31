package com.mimir.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class DraftRevisionTurnMigrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.6-pg17")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PGVECTOR_IMAGE)
            .withDatabaseName("mimir_revision_turn_migration_test")
            .withUsername("mimir")
            .withPassword("mimir_test");

    @Test
    void addsNullableDraftOnlyPreviousTurnForeignKeyAndRejectsSelfLinks() throws Exception {
        migrateTo("8");
        UUID postId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID firstTurnId = UUID.randomUUID();
        UUID secondTurnId = UUID.randomUUID();
        UUID imageJobId = UUID.randomUUID();
        try (Connection connection = connection()) {
            insertBlogPost(connection, postId);
            insertDraftVersion(connection, versionId, postId);
            insertJob(connection, firstTurnId, postId, "BLOG_DRAFT_GENERATION", versionId, "FULL");
            insertJob(connection, secondTurnId, postId, "BLOG_DRAFT_GENERATION", versionId, "FULL");
            insertJob(connection, imageJobId, postId, "IMAGE_ANALYSIS", null, null);
        }

        migrateTo("9");

        try (Connection connection = connection()) {
            setPreviousTurn(connection, secondTurnId, firstTurnId);
            assertThat(previousTurnId(connection, firstTurnId)).isNull();
            assertThat(previousTurnId(connection, secondTurnId)).isEqualTo(firstTurnId);
            assertThatThrownBy(() -> setPreviousTurn(connection, secondTurnId, secondTurnId))
                    .hasMessageContaining("ai_jobs_previous_turn_check");
            assertThatThrownBy(() -> setPreviousTurn(connection, imageJobId, firstTurnId))
                    .hasMessageContaining("ai_jobs_previous_turn_check");
            assertThatThrownBy(() -> setPreviousTurn(connection, secondTurnId, UUID.randomUUID()))
                    .hasMessageContaining("ai_jobs_previous_turn_fk");
        }
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target(version)
                .load()
                .migrate();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private void insertBlogPost(Connection connection, UUID postId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO blog_posts (id, title, status, created_at, updated_at)
                VALUES (?, 'migration test', 'DRAFT', ?, ?)
                """)) {
            statement.setObject(1, postId);
            statement.setObject(2, OffsetDateTime.now());
            statement.setObject(3, OffsetDateTime.now());
            statement.executeUpdate();
        }
    }

    private void insertDraftVersion(Connection connection, UUID versionId, UUID postId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO blog_draft_versions
                    (id, blog_post_id, version_number, source, title, body, created_at)
                VALUES (?, ?, 1, 'USER_EDIT', 'migration test', 'body', ?)
                """)) {
            statement.setObject(1, versionId);
            statement.setObject(2, postId);
            statement.setObject(3, OffsetDateTime.now());
            statement.executeUpdate();
        }
    }

    private void insertJob(
            Connection connection,
            UUID jobId,
            UUID postId,
            String jobType,
            UUID baseVersionId,
            String target) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ai_jobs (
                    id, blog_post_id, job_type, status, stage, total_items,
                    processed_items, failed_items, progress, created_at,
                    base_version_id, revision_instruction, generation_target
                ) VALUES (?, ?, ?, 'COMPLETED', 'COMPLETE', 1, 1, 0, 100, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, jobId);
            statement.setObject(2, postId);
            statement.setString(3, jobType);
            statement.setObject(4, OffsetDateTime.now());
            statement.setObject(5, baseVersionId);
            statement.setString(6, "BLOG_DRAFT_GENERATION".equals(jobType) ? "test" : null);
            statement.setString(7, target);
            statement.executeUpdate();
        }
    }

    private void setPreviousTurn(Connection connection, UUID jobId, UUID previousTurnId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE ai_jobs SET previous_turn_id = ? WHERE id = ?")) {
            statement.setObject(1, previousTurnId);
            statement.setObject(2, jobId);
            statement.executeUpdate();
        }
    }

    private UUID previousTurnId(Connection connection, UUID jobId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT previous_turn_id FROM ai_jobs WHERE id = ?")) {
            statement.setObject(1, jobId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getObject(1, UUID.class);
            }
        }
    }
}
