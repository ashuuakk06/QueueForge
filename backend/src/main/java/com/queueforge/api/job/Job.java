package com.queueforge.api.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.queueforge.api.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "jobs")
public class Job {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private JobType type;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private JobStatus status;

    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")
    private JsonNode result;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(nullable = false) private int priority = 5;
    @Column(nullable = false) private int attempts = 0;
    @Column(name = "max_attempts", nullable = false) private int maxAttempts = 3;
    @Column(name = "retry_delay_seconds", nullable = false) private int retryDelaySeconds = 30;
    @Column(name = "run_at", nullable = false) private Instant runAt;
    @Column(name = "available_at", nullable = false) private Instant availableAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "locked_at") private Instant lockedAt;
    @Column(name = "dispatched_at") private Instant dispatchedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "job_dependencies",
            joinColumns = @JoinColumn(name = "job_id"),
            inverseJoinColumns = @JoinColumn(name = "depends_on_job_id"))
    private Set<Job> dependencies = new HashSet<>();

    protected Job() {}

    public Job(AppUser owner, String name, JobType type, JsonNode payload, int priority,
               int maxAttempts, int retryDelaySeconds, Instant runAt, Set<Job> dependencies) {
        this.owner = owner;
        this.name = name;
        this.type = type;
        this.payload = payload;
        this.priority = priority;
        this.maxAttempts = maxAttempts;
        this.retryDelaySeconds = retryDelaySeconds;
        this.runAt = runAt;
        this.availableAt = runAt;
        this.dependencies = dependencies == null ? new HashSet<>() : new HashSet<>(dependencies);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.status = runAt.isAfter(this.createdAt) ? JobStatus.SCHEDULED
                : (this.dependencies.isEmpty() ? JobStatus.QUEUED : JobStatus.WAITING);
    }

    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getName() { return name; }
    public JobType getType() { return type; }
    public JobStatus getStatus() { return status; }
    public JsonNode getPayload() { return payload; }
    public JsonNode getResult() { return result; }
    public String getErrorMessage() { return errorMessage; }
    public int getPriority() { return priority; }
    public int getAttempts() { return attempts; }
    public int getMaxAttempts() { return maxAttempts; }
    public int getRetryDelaySeconds() { return retryDelaySeconds; }
    public Instant getRunAt() { return runAt; }
    public Instant getAvailableAt() { return availableAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getLockedAt() { return lockedAt; }
    public Instant getDispatchedAt() { return dispatchedAt; }
    public Set<Job> getDependencies() { return dependencies; }

    public void setStatus(JobStatus status) { this.status = status; touch(); }
    public void setResult(JsonNode result) { this.result = result; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setAvailableAt(Instant availableAt) { this.availableAt = availableAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }
    public void setDispatchedAt(Instant dispatchedAt) { this.dispatchedAt = dispatchedAt; }
    public void incrementAttempts() { this.attempts++; }
    public void clearDispatch() { this.dispatchedAt = null; }
    public void resetForManualRetry(Instant now) {
        this.attempts = 0;
        this.result = null;
        this.errorMessage = null;
        this.startedAt = null;
        this.finishedAt = null;
        this.lockedAt = null;
        this.availableAt = now;
        this.dispatchedAt = null;
        setStatus(JobStatus.QUEUED);
    }
    public void touch() { this.updatedAt = Instant.now(); }
}

