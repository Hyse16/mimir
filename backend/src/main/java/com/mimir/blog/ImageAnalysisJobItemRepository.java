package com.mimir.blog;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ImageAnalysisJobItemRepository extends JpaRepository<ImageAnalysisJobItemEntity, UUID> {

    List<ImageAnalysisJobItemEntity> findByJobIdOrderByDisplayOrderAsc(UUID jobId);

    List<ImageAnalysisJobItemEntity> findByJobIdAndStatusOrderByDisplayOrderAsc(
            UUID jobId,
            ImageAnalysisItemStatus status);
}
