package com.queueforge.api.worker;

import com.queueforge.api.job.Job;
import com.queueforge.api.job.JobRepository;
import com.queueforge.api.job.JobService;
import com.queueforge.api.job.JobStatus;
import com.queueforge.api.job.StringRedisQueue;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

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
    public void recoverExpiredLeases() {
        try {
            for (Job job : jobs.findByStatusAndLockedAtBefore(JobStatus.RUNNING, Instant.now().minus(2, ChronoUnit.MINUTES))) {
                jobService.recoverStale(job.getId(), queue);
            }
        } catch (RuntimeException ex) {
            log.warn("Worker recovery will retry: {}", ex.getMessage());
        }
    }
}

