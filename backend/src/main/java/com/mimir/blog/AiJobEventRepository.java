package com.mimir.blog;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AiJobEventRepository extends JpaRepository<AiJobEventEntity, Long> {

    List<AiJobEventEntity> findByJobIdAndIdGreaterThanOrderByIdAsc(UUID jobId, long id);
}
