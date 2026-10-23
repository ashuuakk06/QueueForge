package com.queueforge.api.dashboard;

import com.queueforge.api.config.AppPrincipal;
import com.queueforge.api.event.JobEventService;
import com.queueforge.api.job.Job;
import com.queueforge.api.job.JobRepository;
import com.queueforge.api.job.JobResponse;
import com.queueforge.api.job.JobStatus;
import com.queueforge.api.job.StringRedisQueue;
import com.queueforge.api.worker.WorkerRegistry;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private final JobRepository jobs;
    private final StringRedisQueue queue;
    private final WorkerRegistry workerRegistry;
    private final int workerConcurrency;

    public DashboardController(JobRepository jobs, StringRedisQueue queue, WorkerRegistry workerRegistry,
                               @Value("${app.worker.concurrency:4}") int workerConcurrency) {
        this.jobs = jobs;
        this.queue = queue;
        this.workerRegistry = workerRegistry;
        this.workerConcurrency = workerConcurrency;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public DashboardSummary summary(@AuthenticationPrincipal AppPrincipal principal) {
        UUID owner = principal.id();
        EnumMap<JobStatus, Long> counts = new EnumMap<>(JobStatus.class);
        for (JobStatus status : JobStatus.values()) counts.put(status, jobs.countByOwner_IdAndStatus(owner, status));
        Instant now = Instant.now();
        long today = jobs.countByOwner_IdAndCreatedAtAfter(owner, now.truncatedTo(ChronoUnit.DAYS));
        List<Job> recent = jobs.findTop500ByOwner_IdOrderByCreatedAtDesc(owner);
        long workerInstances = workerRegistry.activeInstances();
        return new DashboardSummary(jobs.countByOwner_Id(owner), today,
                counts.get(JobStatus.QUEUED), counts.get(JobStatus.WAITING) + counts.get(JobStatus.SCHEDULED)
                        + counts.get(JobStatus.RETRY_WAIT),
                counts.get(JobStatus.RUNNING), counts.get(JobStatus.COMPLETED), counts.get(JobStatus.FAILED),
                queue.readySize(), queue.processingSize(), Math.toIntExact(workerInstances * workerConcurrency), workerInstances,
                recent.stream().limit(8).map(JobResponse::from).toList(), activity(recent, now));
    }

    private List<ActivityPoint> activity(List<Job> recent, Instant now) {
        Instant currentHour = now.truncatedTo(ChronoUnit.HOURS);
        List<ActivityPoint> points = new ArrayList<>();
        for (int offset = 11; offset >= 0; offset--) {
            Instant hour = currentHour.minus(offset, ChronoUnit.HOURS);
            Instant end = hour.plus(1, ChronoUnit.HOURS);
            long submitted = recent.stream().filter(job -> !job.getCreatedAt().isBefore(hour) && job.getCreatedAt().isBefore(end)).count();
            long completed = recent.stream().filter(job -> job.getFinishedAt() != null
                    && job.getStatus() == JobStatus.COMPLETED
                    && !job.getFinishedAt().isBefore(hour) && job.getFinishedAt().isBefore(end)).count();
            points.add(new ActivityPoint(HOUR.format(hour), submitted, completed));
        }
        return points;
    }

    public record DashboardSummary(long totalJobs, long jobsToday, long queued, long waiting,
                                   long running, long completed, long failed, long redisReady,
                                   long redisProcessing, int workers, long workerInstances, List<JobResponse> recentJobs,
                                   List<ActivityPoint> activity) {}
    public record ActivityPoint(String hour, long submitted, long completed) {}
}

