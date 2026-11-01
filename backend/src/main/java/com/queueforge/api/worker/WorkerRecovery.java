package com.queueforge.api.worker;

import com.queueforge.api.job.Job;
import com.queueforge.api.job.JobRepository;
import com.queueforge.api.job.JobService;
import com.queueforge.api.job.JobStatus;
import com.queueforge.api.job.StringRedisQueue;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true", matchIfMissing = true)
public class WorkerRecovery {
    private static final Logger log = LoggerFactory.getLogger(WorkerRecovery.class);
    private final JobRepository jobs;
    private final JobService jobService;
    private final StringRedisQueue queue;

    public WorkerRecovery(JobRepository jobs, JobService jobService, StringRedisQueue queue) {
        this.jobs = jobs;
        this.jobService = jobService;
        this.queue = queue;
    }

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void recoverExpiredLeases() {
        try {
            Instant cutoff = Instant.now().minus(2, ChronoUnit.MINUTES);
            for (Job job : jobs.findTop100ByStatusAndLockedAtBeforeOrderByLockedAtAsc(JobStatus.RUNNING, cutoff)) {
                jobService.recoverStale(job.getId(), cutoff, queue);
            }
            acknowledgeFinishedMessages();
        } catch (RuntimeException ex) {
            log.warn("Worker recovery will retry: {}", ex.getMessage());
        }
    }

    private void acknowledgeFinishedMessages() {
        List<String> messages = queue.oldestProcessingIds(1_000);
        Set<String> finished = new HashSet<>();
        Set<UUID> ids = new HashSet<>();
        for (String message : messages) {
            try { ids.add(UUID.fromString(message)); }
            catch (IllegalArgumentException ex) { finished.add(message); }
        }

        var existingJobs = jobs.findAllById(ids);
        Set<UUID> found = existingJobs.stream().map(Job::getId).collect(Collectors.toSet());
        Set<UUID> notActive = existingJobs.stream()
                .filter(job -> job.getStatus() != JobStatus.RUNNING && job.getStatus() != JobStatus.QUEUED)
                .map(Job::getId).collect(Collectors.toSet());
        ids.removeAll(found);
        for (UUID id : ids) finished.add(id.toString());
        for (UUID id : notActive) finished.add(id.toString());
        finished.forEach(queue::ack);
    }
}

