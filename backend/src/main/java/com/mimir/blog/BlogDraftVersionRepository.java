package com.mimir.blog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BlogDraftVersionRepository extends JpaRepository<BlogDraftVersionEntity, UUID> {

    List<BlogDraftVersionEntity> findByBlogPostIdOrderByVersionNumberDesc(UUID blogPostId);

    Optional<BlogDraftVersionEntity> findByIdAndBlogPostId(UUID id, UUID blogPostId);

    @Query("SELECT COALESCE(MAX(version.versionNumber), 0) FROM BlogDraftVersionEntity version WHERE version.blogPostId = :postId")
    int findMaximumVersionNumber(@Param("postId") UUID postId);
}
