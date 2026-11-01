package com.queueforge.api.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queueforge.api.event.JobEventService;
import com.queueforge.api.job.Job;
import com.queueforge.api.job.JobRepository;
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

class JobDispatchSchedulerTest {
    @Test
    void failedPrerequisiteFailsDependentJobWithoutPublishingIt() throws Exception {
        AppUser owner = new AppUser("Ada", "ada@example.com", "hash");
        setId(owner, UUID.randomUUID());
        Job prerequisite = new Job(owner, "Prerequisite", JobType.ECHO, new ObjectMapper().createObjectNode(),
                5, 1, 1, Instant.now(), Set.of());
        setId(prerequisite, UUID.randomUUID());
        prerequisite.setStatus(JobStatus.FAILED);
        Job dependent = new Job(owner, "Dependent", JobType.ECHO, new ObjectMapper().createObjectNode(),
                5, 2, 1, Instant.now(), Set.of(prerequisite));
        setId(dependent, UUID.randomUUID());

        JobRepository jobs = mock(JobRepository.class);
        StringRedisQueue queue = mock(StringRedisQueue.class);
        JobEventService events = mock(JobEventService.class);
        when(jobs.findTop100ByStatusAndDispatchedAtBeforeOrderByDispatchedAtAsc(any(), any())).thenReturn(List.of());
        when(jobs.findTop100ByStatusInAndDispatchedAtIsNullAndAvailableAtLessThanEqualOrderByPriorityDescCreatedAtAsc(
                anyCollection(), any())).thenReturn(List.of(dependent));

        new JobDispatchScheduler(jobs, queue, events).dispatchDueJobs();

        assertEquals(JobStatus.FAILED, dependent.getStatus());
        assertEquals("A prerequisite job failed or was cancelled", dependent.getErrorMessage());
        verify(queue, never()).publish(dependent.getId());
        verify(events).record(dependent, "dependency_failed", "Job stopped because a prerequisite did not complete");
    }

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
