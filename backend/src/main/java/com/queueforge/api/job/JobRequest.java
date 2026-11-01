package com.queueforge.api.job;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobRequest(
        @NotBlank @Size(max = 160) String name,
        @NotNull JobType type,
        JsonNode payload,
        @Min(1) @Max(10) Integer maxAttempts,
        @Min(1) @Max(3600) Integer retryDelaySeconds,
        @Min(1) @Max(10) Integer priority,
        Instant runAt,
        List<UUID> dependsOn) {}

