package com.mimir.blog;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface BlogPostRepository extends JpaRepository<BlogPostEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT post FROM BlogPostEntity post WHERE post.id = :postId")
    Optional<BlogPostEntity> findByIdForUpdate(@Param("postId") UUID postId);

    Page<BlogPostEntity> findByStatus(BlogPostStatus status, Pageable pageable);

    Page<BlogPostEntity> findByTitleContainingIgnoreCase(String query, Pageable pageable);

    Page<BlogPostEntity> findByStatusAndTitleContainingIgnoreCase(
            BlogPostStatus status,
            String query,
            Pageable pageable);
}
