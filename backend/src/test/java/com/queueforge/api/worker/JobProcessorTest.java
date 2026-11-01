package com.queueforge.api.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queueforge.api.job.JobService.ExecutionData;
import com.queueforge.api.job.JobType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobProcessorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final JobProcessor processor = new JobProcessor(mapper);

    @Test
    void echoJobReturnsItsJsonPayload() {
        var payload = mapper.createObjectNode().put("message", "hello").put("source", "test");

        var result = processor.execute(new ExecutionData(UUID.randomUUID(), JobType.ECHO, payload, 1, 3));

        assertEquals(payload, result);
    }

    @Test
    void simulatedFailureStopsFailingAfterTheRequestedAttempt() {
        var payload = mapper.createObjectNode().put("failUntilAttempt", 1);

        assertThrows(IllegalStateException.class, () -> processor.execute(
                new ExecutionData(UUID.randomUUID(), JobType.SIMULATE_FAILURE, payload, 1, 3)));
        var result = processor.execute(new ExecutionData(UUID.randomUUID(), JobType.SIMULATE_FAILURE, payload, 2, 3));

        assertEquals(true, result.path("recovered").asBoolean());
        assertEquals(2, result.path("attempts").asInt());
    }

    @Test
    void delayHandlerPreservesWorkerInterruption() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(IllegalStateException.class, () -> processor.execute(new ExecutionData(
                    UUID.randomUUID(), JobType.DELAY, mapper.createObjectNode().put("milliseconds", 100), 1, 1)));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
