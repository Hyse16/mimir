package com.mimir.blog;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface BlogPostRepository extends JpaRepository<BlogPostEntity, UUID> {

    Page<BlogPostEntity> findByStatus(BlogPostStatus status, Pageable pageable);

    Page<BlogPostEntity> findByTitleContainingIgnoreCase(String query, Pageable pageable);

    Page<BlogPostEntity> findByStatusAndTitleContainingIgnoreCase(
            BlogPostStatus status,
            String query,
            Pageable pageable);
}
