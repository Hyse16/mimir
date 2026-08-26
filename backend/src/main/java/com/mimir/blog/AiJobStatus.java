package com.mimir.blog;

enum AiJobStatus {
    WAITING,
    RUNNING,
    CANCEL_REQUESTED,
    COMPLETED,
    PARTIAL_FAILED,
    FAILED,
    CANCELLED
}
