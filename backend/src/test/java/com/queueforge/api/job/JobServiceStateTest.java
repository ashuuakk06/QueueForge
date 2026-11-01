package com.queueforge.api.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queueforge.api.config.AppPrincipal;
import com.queueforge.api.event.JobEventService;
import com.queueforge.api.user.AppUser;
import com.queueforge.api.user.AppUserRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class JobServiceStateTest {
    @Mock private JobRepository jobs;
    @Mock private AppUserRepository users;
    @Mock private JobEventService events;
    @Mock private StringRedisQueue queue;

    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private AppUser owner;
    private JobService service;

    @BeforeEach
    void setUp() throws Exception {
        owner = new AppUser("Ada Lovelace", "ada@example.com", "hash");
        setId(owner, ownerId);
        service = new JobService(jobs, users, mapper, events);
    }

    @Test
    void claimRunsOnlyOnceWhenRedisDeliversADuplicateMessage() throws Exception {
        Job job = job(Set.of());
        when(jobs.lockById(jobId)).thenReturn(java.util.Optional.of(job));

        JobService.ExecutionData first = service.claim(jobId);
        JobService.ExecutionData duplicate = service.claim(jobId);

        assertNotNull(first);
        assertEquals(1, first.attempt());
        assertNull(duplicate);
        assertEquals(JobStatus.RUNNING, job.getStatus());
        assertEquals(1, job.getAttempts());
    }

    @Test
    void failedAttemptWaitsBeforeRetryAndSecondAttemptCanComplete() throws Exception {
        Job job = job(Set.of());
        when(jobs.lockById(jobId)).thenReturn(java.util.Optional.of(job));

        JobService.ExecutionData first = service.claim(jobId);
        service.failAttempt(jobId, first.attempt(), "temporary failure");
        Instant nextAttemptAt = job.getAvailableAt();

        assertEquals(JobStatus.RETRY_WAIT, job.getStatus());
        assertEquals("temporary failure", job.getErrorMessage());
        assertNotNull(nextAttemptAt);
        org.junit.jupiter.api.Assertions.assertTrue(nextAttemptAt.isAfter(Instant.now().plusSeconds(2)));
        assertEquals(1, job.getAttempts());

        job.setStatus(JobStatus.QUEUED); // The dispatcher makes it ready after the delay.
        JobService.ExecutionData second = service.claim(jobId);
        var result = mapper.createObjectNode().put("ok", true);
        service.complete(jobId, second.attempt(), result);

        assertEquals(2, second.attempt());
        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertEquals(result, job.getResult());
        assertNull(job.getErrorMessage());
    }

    @Test
    void oldWorkerCannotCompleteAJobAfterARecoveryAndNewClaim() throws Exception {
        Job job = job(Set.of());
        job.setStatus(JobStatus.RUNNING);
        job.incrementAttempts();
        job.incrementAttempts();
        when(jobs.lockById(jobId)).thenReturn(java.util.Optional.of(job));

        service.complete(jobId, 1, mapper.createObjectNode().put("stale", true));

        assertEquals(JobStatus.RUNNING, job.getStatus());
        assertNull(job.getResult());
    }

    @Test
    void exhaustedAttemptsBecomeFailed() throws Exception {
        Job job = job(Set.of());
        job.setStatus(JobStatus.RUNNING);
        job.incrementAttempts();
        job.incrementAttempts();
        when(jobs.lockById(jobId)).thenReturn(java.util.Optional.of(job));

        service.failAttempt(jobId, 2, "last attempt failed");

        assertEquals(JobStatus.FAILED, job.getStatus());
        assertEquals("last attempt failed", job.getErrorMessage());
        assertNotNull(job.getFinishedAt());
    }

    @Test
    void staleLeaseIsRequeuedButARecentLeaseIsLeftAlone() throws Exception {
        Job stale = job(Set.of());
        stale.setStatus(JobStatus.RUNNING);
        stale.incrementAttempts();
        stale.setLockedAt(Instant.now().minusSeconds(180));
        when(jobs.lockById(jobId)).thenReturn(java.util.Optional.of(stale));
        Instant cutoff = Instant.now().minusSeconds(120);

        service.recoverStale(jobId, cutoff, queue);

        assertEquals(JobStatus.RETRY_WAIT, stale.getStatus());
        verify(queue).ack(jobId.toString());

        Job recent = job(Set.of());
        recent.setStatus(JobStatus.RUNNING);
        recent.incrementAttempts();
        recent.setLockedAt(Instant.now());
        UUID recentId = UUID.randomUUID();
        setId(recent, recentId);
        when(jobs.lockById(recentId)).thenReturn(java.util.Optional.of(recent));
        service.recoverStale(recentId, cutoff, queue);
        assertEquals(JobStatus.RUNNING, recent.getStatus());
        verify(queue, never()).ack(recentId.toString());
    }

    @Test
    void eventHistoryCannotBeReadByAnotherAccount() throws Exception {
        UUID otherOwner = UUID.randomUUID();
        AppPrincipal principal = new AppPrincipal(otherOwner, "Other", "other@example.com", "hash");
        when(jobs.findByIdAndOwner_Id(jobId, otherOwner)).thenReturn(java.util.Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.events(principal, jobId));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(events, never()).list(any(), any());
    }

    private Job job(Set<Job> dependencies) throws Exception {
        Job job = new Job(owner, "Test work", JobType.ECHO, mapper.createObjectNode().put("message", "hello"),
                5, 2, 3, Instant.now(), dependencies);
        setId(job, jobId);
        return job;
    }

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
