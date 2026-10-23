package com.queueforge.api.worker;

import com.queueforge.api.job.JobService;
import com.queueforge.api.job.JobService.ExecutionData;
import com.queueforge.api.job.StringRedisQueue;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true", matchIfMissing = true)
public class WorkerEngine {
    private static final Logger log = LoggerFactory.getLogger(WorkerEngine.class);
    private final StringRedisQueue queue;
    private final JobService jobs;
    private final JobProcessor processor;
    private final Executor workerExecutor;

    public WorkerEngine(StringRedisQueue queue, JobService jobs, JobProcessor processor,
                        @Qualifier("jobWorkerExecutor") Executor workerExecutor) {
        this.queue = queue;
        this.jobs = jobs;
        this.processor = processor;
        this.workerExecutor = workerExecutor;
    }

    @Scheduled(fixedDelay = 150)
    public void poll() {
        try {
            String id = queue.receive();
            if (id != null) workerExecutor.execute(() -> process(id));
        } catch (RuntimeException ex) {
            log.debug("Queue poll will retry: {}", ex.getMessage());
        }
    }

    private void process(String rawId) {
        try {
            ExecutionData execution = jobs.claim(UUID.fromString(rawId));
            if (execution == null) return;
            try {
                jobs.complete(execution.id(), processor.execute(execution));
            } catch (RuntimeException ex) {
                log.warn("Job {} attempt {} failed: {}", execution.id(), execution.attempt(), ex.getMessage());
                jobs.failAttempt(execution.id(), ex.getMessage());
            }
        } catch (RuntimeException ex) {
            log.error("Could not process queued job {}: {}", rawId, ex.getMessage());
        } finally {
            try { queue.ack(rawId); }
            catch (RuntimeException ex) { log.warn("Could not acknowledge job {}: {}", rawId, ex.getMessage()); }
        }
    }
}

