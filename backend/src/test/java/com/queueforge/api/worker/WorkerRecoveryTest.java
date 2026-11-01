package com.queueforge.api.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queueforge.api.job.Job;
import com.queueforge.api.job.JobRepository;
import com.queueforge.api.job.JobService;
import com.queueforge.api.job.JobStatus;
import com.queueforge.api.job.JobType;
import com.queueforge.api.job.StringRedisQueue;
import com.queueforge.api.user.AppUser;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkerRecoveryTest {
    @Test
    void acknowledgesOnlyOrphanedOrNoLongerActiveProcessingMessages() throws Exception {
        UUID completeId = UUID.randomUUID();
        UUID retryId = UUID.randomUUID();
        UUID runningId = UUID.randomUUID();
        UUID queuedId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        Job completed = job(completeId, JobStatus.COMPLETED);
        Job retrying = job(retryId, JobStatus.RETRY_WAIT);
        Job running = job(runningId, JobStatus.RUNNING);
        Job queued = job(queuedId, JobStatus.QUEUED);

        JobRepository jobs = mock(JobRepository.class);
        JobService service = mock(JobService.class);
        StringRedisQueue queue = mock(StringRedisQueue.class);
        when(jobs.findTop100ByStatusAndLockedAtBeforeOrderByLockedAtAsc(any(), any())).thenReturn(List.of());
        when(queue.oldestProcessingIds(1_000)).thenReturn(List.of(completeId.toString(), retryId.toString(),
                runningId.toString(), queuedId.toString(), missingId.toString(), "bad-message"));
        when(jobs.findAllById(anySet())).thenReturn(List.of(completed, retrying, running, queued));

        new WorkerRecovery(jobs, service, queue).recoverExpiredLeases();

        verify(queue).ack(completeId.toString());
        verify(queue).ack(retryId.toString());
        verify(queue).ack(missingId.toString());
        verify(queue).ack("bad-message");
        verify(queue, never()).ack(runningId.toString());
        verify(queue, never()).ack(queuedId.toString());
    }

    private static Job job(UUID id, JobStatus status) throws Exception {
        AppUser owner = new AppUser("Ada", "ada@example.com", "hash");
        setId(owner, UUID.randomUUID());
        Job job = new Job(owner, "Recovery test", JobType.ECHO, new ObjectMapper().createObjectNode(),
                5, 3, 1, Instant.now(), Set.of());
        setId(job, id);
        job.setStatus(status);
        return job;
    }

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
