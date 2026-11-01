package com.queueforge.api.job;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queueforge.api.user.AppUser;
import com.queueforge.api.user.AppUserRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:queueforge-claims;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackageClasses = {Job.class, AppUser.class})
@EnableJpaRepositories(basePackageClasses = {JobRepository.class, AppUserRepository.class})
class JobClaimConcurrencyTest {
    @Autowired private JobRepository jobs;
    @Autowired private AppUserRepository users;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void concurrentWorkersCanClaimOnlyOneAttempt() throws Exception {
        AppUser owner = users.saveAndFlush(new AppUser("Ada", "ada@example.com", "hash"));
        Job job = jobs.saveAndFlush(new Job(owner, "Concurrent claim", JobType.ECHO,
                new ObjectMapper().createObjectNode().put("message", "hello"), 5, 3, 1,
                Instant.now(), Set.of()));
        UUID jobId = job.getId();
        TestTransaction.flagForCommit();
        TestTransaction.end();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = workers.submit(() -> claim(jobId, ready, start, transactions));
            Future<Boolean> second = workers.submit(() -> claim(jobId, ready, start, transactions));
            if (!ready.await(5, TimeUnit.SECONDS)) throw new AssertionError("Workers did not reach the start gate");
            start.countDown();

            int claims = (first.get(5, TimeUnit.SECONDS) ? 1 : 0) + (second.get(5, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, claims);
        }

        assertEquals(JobStatus.RUNNING, jobs.findById(jobId).orElseThrow().getStatus());
        assertEquals(1, jobs.findById(jobId).orElseThrow().getAttempts());
    }

    private boolean claim(UUID jobId, CountDownLatch ready, CountDownLatch start,
                          TransactionTemplate transactions) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Worker start gate timed out");
        return transactions.execute(status -> {
            Job job = jobs.lockById(jobId).orElseThrow();
            if (job.getStatus() != JobStatus.QUEUED) return false;
            job.incrementAttempts();
            job.setStatus(JobStatus.RUNNING);
            return true;
        });
    }
}
