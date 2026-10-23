package com.queueforge.api.event;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("job_events")
public class JobEventDocument {
    @Id private String id;
    private String jobId;
    private String userId;
    private String jobName;
    private String event;
    private String status;
    private String message;
    private Instant occurredAt;

    public JobEventDocument(String jobId, String userId, String jobName, String event, String status, String message) {
        this.jobId = jobId;
        this.userId = userId;
        this.jobName = jobName;
        this.event = event;
        this.status = status;
        this.message = message;
        this.occurredAt = Instant.now();
    }

    public String getId() { return id; }
    public String getJobId() { return jobId; }
    public String getUserId() { return userId; }
    public String getJobName() { return jobName; }
    public String getEvent() { return event; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getOccurredAt() { return occurredAt; }
}

