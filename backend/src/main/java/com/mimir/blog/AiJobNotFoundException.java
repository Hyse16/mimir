package com.mimir.blog;

import java.util.UUID;

public class AiJobNotFoundException extends RuntimeException {

    AiJobNotFoundException(UUID jobId) {
        super("AI job was not found: " + jobId);
    }
}
