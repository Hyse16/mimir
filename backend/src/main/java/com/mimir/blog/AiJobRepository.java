package com.mimir.blog;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface AiJobRepository extends JpaRepository<AiJobEntity, UUID> {

    boolean existsByBlogPostIdAndStatusIn(UUID blogPostId, Collection<AiJobStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from AiJobEntity job where job.id = :id")
    Optional<AiJobEntity> findByIdForUpdate(@Param("id") UUID id);
}
