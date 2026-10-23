package com.queueforge.api.event;

import com.queueforge.api.job.Job;
import com.queueforge.api.job.JobStatus;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JobEventService {
    private static final Logger log = LoggerFactory.getLogger(JobEventService.class);
    private final JobEventRepository events;
    public JobEventService(JobEventRepository events) { this.events = events; }

    public void record(Job job, String event, String message) {
        try {
            events.save(new JobEventDocument(job.getId().toString(), job.getOwner().getId().toString(),
                    job.getName(), event, job.getStatus().name(), message));
        } catch (RuntimeException ex) {
            log.warn("Could not write job event {} for {}: {}", event, job.getId(), ex.getMessage());
        }
    }

    public List<JobEventDocument> list(UUID jobId, UUID userId) {
        return events.findTop50ByJobIdAndUserIdOrderByOccurredAtDesc(jobId.toString(), userId.toString());
    }
}

