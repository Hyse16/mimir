package com.mimir.blog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BlogAssetRepository extends JpaRepository<BlogAssetEntity, UUID> {

    List<BlogAssetEntity> findByBlogPostIdOrderByDisplayOrderAsc(UUID blogPostId);

    Optional<BlogAssetEntity> findByIdAndBlogPostId(UUID id, UUID blogPostId);

    long countByBlogPostId(UUID blogPostId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE blog_assets SET display_order = display_order + 1000 WHERE blog_post_id = :postId", nativeQuery = true)
    void moveOrdersOutOfRange(@Param("postId") UUID postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE blog_assets SET display_order = :displayOrder WHERE id = :assetId", nativeQuery = true)
    void updateDisplayOrder(@Param("assetId") UUID assetId, @Param("displayOrder") int displayOrder);
}
