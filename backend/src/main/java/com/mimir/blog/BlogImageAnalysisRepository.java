package com.mimir.blog;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface BlogImageAnalysisRepository extends JpaRepository<BlogImageAnalysisEntity, UUID> {

    List<BlogImageAnalysisEntity> findByJobId(UUID jobId);
}
