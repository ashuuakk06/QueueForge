package com.queueforge.api.event;

import com.queueforge.api.job.Job;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_events")
public class JobEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "job_name", nullable = false, length = 160)
    private String jobName;

    @Column(nullable = false, length = 60)
    private String event;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(columnDefinition = "text")
    private String message;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected JobEvent() {}

    public JobEvent(Job job, String event, String message) {
        this.jobId = job.getId();
        this.userId = job.getOwner().getId();
        this.jobName = job.getName();
        this.event = event;
        this.status = job.getStatus().name();
        this.message = message;
        this.occurredAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getJobId() { return jobId; }
    public UUID getUserId() { return userId; }
    public String getJobName() { return jobName; }
    public String getEvent() { return event; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getOccurredAt() { return occurredAt; }
}
