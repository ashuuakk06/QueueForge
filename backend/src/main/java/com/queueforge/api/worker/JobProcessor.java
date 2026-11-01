package com.queueforge.api.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.queueforge.api.job.JobService.ExecutionData;
import com.queueforge.api.job.JobType;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class JobProcessor {
    private final ObjectMapper mapper;
    public JobProcessor(ObjectMapper mapper) { this.mapper = mapper; }

    public JsonNode execute(ExecutionData job) {
        JsonNode payload = job.payload();
        return switch (job.type()) {
            case ECHO -> payload.deepCopy();
            case TRANSFORM -> transform(payload);
            case DELAY -> delay(payload);
            case SIMULATE_FAILURE -> failThenRecover(payload, job);
        };
    }

    private JsonNode transform(JsonNode payload) {
        String text = payload.path("text").asText("");
        ObjectNode result = mapper.createObjectNode();
        result.put("original", text);
        result.put("uppercase", text.toUpperCase());
        result.put("characters", text.length());
        result.put("processedAt", Instant.now().toString());
        return result;
    }

    private JsonNode delay(JsonNode payload) {
        int requested = payload.path("milliseconds").asInt(1000);
        int milliseconds = Math.max(0, Math.min(30_000, requested));
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Worker was interrupted", ex);
        }
        ObjectNode result = mapper.createObjectNode();
        result.put("waitedMilliseconds", milliseconds);
        result.put("completedAt", Instant.now().toString());
        return result;
    }

    private JsonNode failThenRecover(JsonNode payload, ExecutionData job) {
        int failUntilAttempt = Math.max(0, payload.path("failUntilAttempt").asInt(1));
        if (job.attempt() <= failUntilAttempt) {
            throw new IllegalStateException("Demo failure on attempt " + job.attempt());
        }
        ObjectNode result = mapper.createObjectNode();
        result.put("recovered", true);
        result.put("attempts", job.attempt());
        result.put("message", "The retry policy recovered this job");
        return result;
    }
}

