package com.queueforge.api.worker;

import com.queueforge.api.event.JobEventService;
import com.queueforge.api.job.Job;
import com.queueforge.api.job.JobRepository;
import com.queueforge.api.job.JobStatus;
import com.queueforge.api.job.StringRedisQueue;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class JobDispatchScheduler {
    private static final Logger log = LoggerFactory.getLogger(JobDispatchScheduler.class);
    private static final List<JobStatus> DISPATCHABLE = List.of(
            JobStatus.SCHEDULED, JobStatus.WAITING, JobStatus.QUEUED, JobStatus.RETRY_WAIT);
    private final JobRepository jobs;
    private final StringRedisQueue queue;
    private final JobEventService events;

    public JobDispatchScheduler(JobRepository jobs, StringRedisQueue queue, JobEventService events) {
        this.jobs = jobs;
        this.queue = queue;
        this.events = events;
    }

    @Scheduled(fixedDelay = 750)
    @Transactional
    public void dispatchDueJobs() {
        try {
            Instant now = Instant.now();
            for (Job job : jobs.findTop100ByStatusInAndAvailableAtLessThanEqualOrderByPriorityDescCreatedAtAsc(DISPATCHABLE, now)) {
                boolean failedDependency = job.getDependencies().stream()
                        .anyMatch(dependency -> dependency.getStatus() == JobStatus.FAILED || dependency.getStatus() == JobStatus.CANCELLED);
                if (failedDependency) {
                    job.setErrorMessage("A prerequisite job failed or was cancelled");
                    job.setFinishedAt(now);
                    job.setStatus(JobStatus.FAILED);
                    jobs.save(job);
                    events.record(job, "dependency_failed", "Job stopped because a prerequisite did not complete");
                    continue;
                }

                boolean dependenciesComplete = job.getDependencies().stream()
                        .allMatch(dependency -> dependency.getStatus() == JobStatus.COMPLETED);
                if (!dependenciesComplete) {
                    if (job.getStatus() == JobStatus.SCHEDULED) job.setStatus(JobStatus.WAITING);
                    continue;
                }

                if (job.getStatus() != JobStatus.QUEUED) {
                    job.setStatus(JobStatus.QUEUED);
                    events.record(job, "queued", "Schedule and dependencies are clear");
                }
                if (job.getDispatchedAt() == null) {
                    queue.publish(job.getId());
                    job.setDispatchedAt(now);
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Queue dispatch will retry: {}", ex.getMessage());
        }
    }
}

