package com.queueforge.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.queueforge.api.job.Job;
import com.queueforge.api.job.JobType;
import com.queueforge.api.user.AppUser;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:queueforge-events;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@EntityScan(basePackageClasses = JobEvent.class)
@EnableJpaRepositories(basePackageClasses = JobEventRepository.class)
class JobEventRepositoryTest {
    @Autowired private JobEventRepository events;
    @Autowired private TestEntityManager entityManager;

    @Test
    void persistsEventsAndListsOnlyTheRequestedJobOwnerInNewestFirstOrder() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        AppUser owner = new AppUser("Ada", "ada@example.com", "hash");
        setId(owner, ownerId);
        Job job = new Job(owner, "Example job", JobType.ECHO, new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode(),
                5, 2, 3, Instant.now(), java.util.Set.of());
        setId(job, jobId);

        JobEvent created = new JobEvent(job, "created", "Submitted");
        setField(created, "occurredAt", Instant.parse("2026-01-01T00:00:00Z"));
        JobEvent started = new JobEvent(job, "started", "Attempt 1 started");
        setField(started, "occurredAt", Instant.parse("2026-01-01T00:00:01Z"));
        JobEvent otherOwner = new JobEvent(job, "created", "Different owner");
        setField(otherOwner, "userId", otherOwnerId);
        entityManager.getEntityManager().persist(created);
        entityManager.getEntityManager().persist(started);
        entityManager.getEntityManager().persist(otherOwner);
        entityManager.flush();
        entityManager.clear();

        List<JobEvent> persisted = events.findTop50ByJobIdAndUserIdOrderByOccurredAtDesc(jobId, ownerId);

        assertEquals(2, persisted.size());
        assertEquals(List.of("started", "created"), persisted.stream().map(JobEvent::getEvent).toList());
        assertEquals(List.of("Attempt 1 started", "Submitted"), persisted.stream().map(JobEvent::getMessage).toList());
    }

    private static void setId(Object entity, UUID id) throws Exception {
        setField(entity, "id", id);
    }

    private static void setField(Object entity, String name, Object value) throws Exception {
        Field field = entity.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(entity, value);
    }
}
