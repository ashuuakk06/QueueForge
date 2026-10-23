package com.queueforge.api.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true", matchIfMissing = true)
public class WorkerHeartbeat {
    private static final Logger log = LoggerFactory.getLogger(WorkerHeartbeat.class);
    private final WorkerRegistry registry;
    public WorkerHeartbeat(WorkerRegistry registry) { this.registry = registry; }

    @Scheduled(fixedRate = 5_000)
    public void publishHeartbeat() {
        try { registry.heartbeat(); }
        catch (RuntimeException ex) { log.debug("Worker heartbeat will retry: {}", ex.getMessage()); }
    }
}
