package com.queueforge.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queueforge.api.job.Job;
import com.queueforge.api.job.JobType;
import com.queueforge.api.user.AppUser;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobEventServiceTest {
    @Mock private JobEventRepository repository;

    @Test
    void recordsEventWithJobOwnerStateAndTimestamp() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        AppUser user = new AppUser("Ada", "ada@example.com", "hash");
        setId(user, userId);
        Job job = new Job(user, "Normalize records", JobType.TRANSFORM,
                new ObjectMapper().createObjectNode().put("text", "acme"), 7, 3, 5, Instant.now(), Set.of());
        setId(job, jobId);

        when(repository.save(any(JobEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        new JobEventService(repository).record(job, "created", "Job submitted to QueueForge");

        ArgumentCaptor<JobEvent> captor = ArgumentCaptor.forClass(JobEvent.class);
        verify(repository).save(captor.capture());
        JobEvent event = captor.getValue();
        assertEquals(jobId, event.getJobId());
        assertEquals(userId, event.getUserId());
        assertEquals("Normalize records", event.getJobName());
        assertEquals("created", event.getEvent());
        assertEquals(job.getStatus().name(), event.getStatus());
        assertEquals("Job submitted to QueueForge", event.getMessage());
        org.junit.jupiter.api.Assertions.assertNotNull(event.getOccurredAt());
    }

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
