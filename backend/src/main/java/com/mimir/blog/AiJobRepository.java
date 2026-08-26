package com.mimir.blog;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AiJobRepository extends JpaRepository<AiJobEntity, UUID> {

    boolean existsByBlogPostIdAndStatusIn(UUID blogPostId, Collection<AiJobStatus> statuses);
}
