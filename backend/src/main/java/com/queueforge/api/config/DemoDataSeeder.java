package com.queueforge.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queueforge.api.job.JobRequest;
import com.queueforge.api.job.JobResponse;
import com.queueforge.api.job.JobType;
import com.queueforge.api.job.JobService;
import com.queueforge.api.user.AppUser;
import com.queueforge.api.user.AppUserRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.demo.seed", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final JobService jobs;
    private final ObjectMapper mapper;

    public DemoDataSeeder(AppUserRepository users, PasswordEncoder passwords, JobService jobs, ObjectMapper mapper) {
        this.users = users;
        this.passwords = passwords;
        this.jobs = jobs;
        this.mapper = mapper;
    }

    @Override
    public void run(String... args) throws Exception {
        if (users.existsByEmailIgnoreCase("demo@queueforge.dev")) return;
        AppUser user = users.save(new AppUser("Alex Morgan", "demo@queueforge.dev", passwords.encode("QueueForge!2026")));

        JobResponse normalize = jobs.createForUser(user, request("Normalize customer records", JobType.TRANSFORM,
                "{\"text\":\"acme supply order 4821\"}", 3, 5, Instant.now(), List.of()));
        jobs.createForUser(user, request("Send completion notification", JobType.ECHO,
                "{\"channel\":\"operations\",\"message\":\"Customer import finished\"}", 3, 4,
                Instant.now(), List.of(normalize.id())));
        jobs.createForUser(user, request("Build weekly report", JobType.DELAY,
                "{\"milliseconds\":18000}", 3, 6, Instant.now(), List.of()));
        JobResponse retry = jobs.createForUser(user, request("Retry policy showcase", JobType.SIMULATE_FAILURE,
                "{\"failUntilAttempt\":2}", 4, 3, Instant.now(), List.of()));
        jobs.createForUser(user, request("Publish retry result", JobType.ECHO,
                "{\"channel\":\"reports\",\"message\":\"Retry recovered\"}", 3, 5,
                Instant.now(), List.of(retry.id())));
        jobs.createForUser(user, request("Nightly archive", JobType.ECHO,
                "{\"bucket\":\"archive\",\"scope\":\"daily\"}", 3, 3,
                Instant.now().plusSeconds(600), List.of()));
        log.info("QueueForge demo account ready: demo@queueforge.dev / QueueForge!2026");
    }

    private JobRequest request(String name, JobType type, String payload, int attempts, int priority,
                               Instant runAt, List<java.util.UUID> dependencies) throws Exception {
        return new JobRequest(name, type, mapper.readTree(payload), attempts, 8, priority, runAt, dependencies);
    }
}

