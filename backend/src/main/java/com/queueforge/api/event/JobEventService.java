package com.queueforge.api.event;

import com.queueforge.api.job.Job;
import com.queueforge.api.job.JobStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class JobEventService {
    private final JobEventRepository events;
    public JobEventService(JobEventRepository events) { this.events = events; }

    public void record(Job job, String event, String message) {
        events.save(new JobEvent(job, event, message));
    }

    public List<JobEvent> list(UUID jobId, UUID userId) {
        return events.findTop50ByJobIdAndUserIdOrderByOccurredAtDesc(jobId, userId);
    }
}

