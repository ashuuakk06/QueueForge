package com.queueforge.api.worker;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true", matchIfMissing = true)
public class WorkerConfiguration {
    @Bean(name = "jobWorkerExecutor")
    Executor jobWorkerExecutor(@Value("${app.worker.concurrency:4}") int concurrency) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("queueforge-worker-");
        executor.initialize();
        return executor;
    }
}

