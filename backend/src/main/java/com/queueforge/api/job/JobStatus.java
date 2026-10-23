package com.queueforge.api.job;

public enum JobStatus {
    SCHEDULED,
    WAITING,
    QUEUED,
    RUNNING,
    RETRY_WAIT,
    COMPLETED,
    FAILED,
    CANCELLED
}

