package com.queueforge.api.worker;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class WorkerRegistry {
    private static final String WORKERS_KEY = "queueforge:workers:online";
    private static final long HEARTBEAT_TTL_SECONDS = 20;
    private final StringRedisTemplate redis;
    private final String workerId;

    public WorkerRegistry(StringRedisTemplate redis, @Value("${app.worker.id:}") String configuredId) {
        this.redis = redis;
        this.workerId = configuredId == null || configuredId.isBlank() ? defaultId() : configuredId.trim();
    }

    public void heartbeat() {
        redis.opsForZSet().add(WORKERS_KEY, workerId, Instant.now().getEpochSecond());
    }

    public long activeInstances() {
        long now = Instant.now().getEpochSecond();
        redis.opsForZSet().removeRangeByScore(WORKERS_KEY, 0, now - HEARTBEAT_TTL_SECONDS);
        Set<String> active = redis.opsForZSet().rangeByScore(WORKERS_KEY, now - HEARTBEAT_TTL_SECONDS, now + 1);
        return active == null ? 0 : active.size();
    }

    private String defaultId() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (Exception ignored) { return "queueforge-worker"; }
    }
}

