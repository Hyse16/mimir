package com.mimir.blog;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface BlogContextRepository extends JpaRepository<BlogContextEntity, UUID> {
}
