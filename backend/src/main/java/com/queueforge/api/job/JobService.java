package com.queueforge.api.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.queueforge.api.config.AppPrincipal;
import com.queueforge.api.event.JobEventService;
import com.queueforge.api.user.AppUser;
import com.queueforge.api.user.AppUserRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobService {
    private final JobRepository jobs;
    private final AppUserRepository users;
    private final ObjectMapper mapper;
    private final JobEventService eventService;

    public JobService(JobRepository jobs, AppUserRepository users, ObjectMapper mapper, JobEventService eventService) {
        this.jobs = jobs;
        this.users = users;
        this.mapper = mapper;
        this.eventService = eventService;
    }

    @Transactional(readOnly = true)
    public List<JobResponse> list(AppPrincipal principal, JobStatus status, String search) {
        String term = search == null ? "" : search.trim().toLowerCase();
        return jobs.findTop500ByOwner_IdOrderByCreatedAtDesc(principal.id()).stream()
                .filter(job -> status == null || job.getStatus() == status)
                .filter(job -> term.isEmpty() || job.getName().toLowerCase().contains(term)
                        || job.getType().name().toLowerCase().contains(term))
                .map(JobResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public JobResponse get(AppPrincipal principal, UUID id) {
        return JobResponse.from(findOwned(principal.id(), id));
    }

    @Transactional
    public JobResponse create(AppPrincipal principal, JobRequest request) {
        return createForUser(users.findById(principal.id()).orElseThrow(), request);
    }

    @Transactional
    public JobResponse createForUser(AppUser owner, JobRequest request) {
        Set<Job> dependencies = new HashSet<>();
        if (request.dependsOn() != null && !request.dependsOn().isEmpty()) {
            List<Job> found = jobs.findAllById(new HashSet<>(request.dependsOn()));
            if (found.size() != new HashSet<>(request.dependsOn()).size()
                    || found.stream().anyMatch(job -> !job.getOwner().getId().equals(owner.getId()))) {
                throw new IllegalArgumentException("Dependencies must belong to your account and already exist");
            }
            dependencies.addAll(found);
        }
        JsonNode payload = request.payload();
        if (payload == null || payload.isNull()) payload = mapper.createObjectNode();
        if (!payload.isObject()) throw new IllegalArgumentException("Payload must be a JSON object");

        Instant now = Instant.now();
        Instant runAt = request.runAt() == null ? now : request.runAt();
        Job job = jobs.save(new Job(owner, request.name().trim(), request.type(), payload,
                request.priority() == null ? 5 : request.priority(),
                request.maxAttempts() == null ? 3 : request.maxAttempts(),
                request.retryDelaySeconds() == null ? 30 : request.retryDelaySeconds(),
                runAt, dependencies));
        eventService.record(job, "created", "Job submitted to QueueForge");
        return JobResponse.from(job);
    }

    @Transactional
    public JobResponse retry(AppPrincipal principal, UUID id) {
        Job job = findOwned(principal.id(), id);
        if (job.getStatus() != JobStatus.FAILED && job.getStatus() != JobStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed or cancelled jobs can be retried");
        }
        job.resetForManualRetry(Instant.now());
        jobs.save(job);
        eventService.record(job, "retried", "Manually retried from the dashboard");
        return JobResponse.from(job);
    }

    @Transactional
    public JobResponse cancel(AppPrincipal principal, UUID id) {
        Job job = findOwned(principal.id(), id);
        if (!(job.getStatus() == JobStatus.SCHEDULED || job.getStatus() == JobStatus.WAITING
                || job.getStatus() == JobStatus.QUEUED || job.getStatus() == JobStatus.RETRY_WAIT)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending jobs can be cancelled");
        }
        job.setStatus(JobStatus.CANCELLED);
        job.setFinishedAt(Instant.now());
        jobs.save(job);
        eventService.record(job, "cancelled", "Cancelled from the dashboard");
        return JobResponse.from(job);
    }

    @Transactional
    public ExecutionData claim(UUID id) {
        Job job = jobs.lockById(id).orElse(null);
        if (job == null || job.getStatus() != JobStatus.QUEUED) return null;
        job.incrementAttempts();
        job.setStatus(JobStatus.RUNNING);
        job.setLockedAt(Instant.now());
        if (job.getStartedAt() == null) job.setStartedAt(Instant.now());
        jobs.save(job);
        eventService.record(job, "started", "Worker picked up attempt " + job.getAttempts());
        return new ExecutionData(job.getId(), job.getType(), job.getPayload().deepCopy(),
                job.getAttempts(), job.getMaxAttempts());
    }

    @Transactional
    public void complete(UUID id, JsonNode result) {
        Job job = jobs.lockById(id).orElse(null);
        if (job == null || job.getStatus() != JobStatus.RUNNING) return;
        job.setResult(result);
        job.setErrorMessage(null);
        job.setLockedAt(null);
        job.setFinishedAt(Instant.now());
        job.setStatus(JobStatus.COMPLETED);
        jobs.save(job);
        eventService.record(job, "completed", "Job completed successfully");
    }

    @Transactional
    public void failAttempt(UUID id, String message) {
        Job job = jobs.lockById(id).orElse(null);
        if (job == null || job.getStatus() != JobStatus.RUNNING) return;
        Instant now = Instant.now();
        job.setErrorMessage(message == null ? "Worker failed without an error message" : message);
        job.setLockedAt(null);
        if (job.getAttempts() < job.getMaxAttempts()) {
            job.setAvailableAt(now.plusSeconds(job.getRetryDelaySeconds()));
            job.clearDispatch();
            job.setStatus(JobStatus.RETRY_WAIT);
            eventService.record(job, "retry_scheduled", "Attempt " + job.getAttempts() + " failed; retry in " + job.getRetryDelaySeconds() + "s");
        } else {
            job.setFinishedAt(now);
            job.setStatus(JobStatus.FAILED);
            eventService.record(job, "failed", "Retry limit reached after " + job.getAttempts() + " attempts");
        }
        jobs.save(job);
    }

    @Transactional
    public void recoverStale(UUID id, StringRedisQueue redisQueue) {
        Job job = jobs.lockById(id).orElse(null);
        if (job == null || job.getStatus() != JobStatus.RUNNING) return;
        redisQueue.ack(id.toString());
        Instant now = Instant.now();
        job.setLockedAt(null);
        if (job.getAttempts() < job.getMaxAttempts()) {
            job.setAvailableAt(now.plusSeconds(job.getRetryDelaySeconds()));
            job.clearDispatch();
            job.setStatus(JobStatus.RETRY_WAIT);
            eventService.record(job, "worker_recovered", "Worker lease expired; retry scheduled");
        } else {
            job.setFinishedAt(now);
            job.setErrorMessage("Worker lease expired after the retry limit was reached");
            job.setStatus(JobStatus.FAILED);
            eventService.record(job, "failed", "Worker lease expired after the retry limit was reached");
        }
        jobs.save(job);
    }

    @Transactional
    public List<JobEventView> events(AppPrincipal principal, UUID id) {
        findOwned(principal.id(), id);
        return eventService.list(id, principal.id()).stream()
                .map(event -> new JobEventView(event.getId(), event.getEvent(), event.getStatus(), event.getMessage(), event.getOccurredAt()))
                .toList();
    }

    @Transactional
    public Job findOwned(UUID ownerId, UUID id) {
        return jobs.findByIdAndOwner_Id(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    }

    public record ExecutionData(UUID id, JobType type, JsonNode payload, int attempt, int maxAttempts) {}
    public record JobEventView(String id, String event, String status, String message, Instant occurredAt) {}
}

