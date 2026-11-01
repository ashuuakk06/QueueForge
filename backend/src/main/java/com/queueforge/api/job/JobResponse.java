package com.queueforge.api.job;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobResponse(UUID id, String name, JobType type, JobStatus status, JsonNode payload,
                          JsonNode result, String errorMessage, int priority, int attempts,
                          int maxAttempts, int retryDelaySeconds, Instant runAt, Instant createdAt,
                          Instant updatedAt, Instant startedAt, Instant finishedAt,
                          List<UUID> dependsOn) {
    public static JobResponse from(Job job) {
        return new JobResponse(job.getId(), job.getName(), job.getType(), job.getStatus(),
                job.getPayload(), job.getResult(), job.getErrorMessage(), job.getPriority(),
                job.getAttempts(), job.getMaxAttempts(), job.getRetryDelaySeconds(),
                job.getRunAt(), job.getCreatedAt(), job.getUpdatedAt(), job.getStartedAt(),
                job.getFinishedAt(), job.getDependencies().stream().map(Job::getId).toList());
    }
}

