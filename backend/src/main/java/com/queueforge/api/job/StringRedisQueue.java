package com.queueforge.api.job;

import java.util.UUID;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class StringRedisQueue {
    public static final String READY_KEY = "queueforge:jobs:ready";
    public static final String PROCESSING_KEY = "queueforge:jobs:processing";
    private final StringRedisTemplate redis;

    public StringRedisQueue(StringRedisTemplate redis) { this.redis = redis; }

    public void publish(UUID jobId) { redis.opsForList().leftPush(READY_KEY, jobId.toString()); }
    public String receive() { return redis.opsForList().rightPopAndLeftPush(READY_KEY, PROCESSING_KEY); }
    public void ack(String jobId) { redis.opsForList().remove(PROCESSING_KEY, 0, jobId); }
    public List<String> oldestProcessingIds(int limit) {
        List<String> ids = redis.opsForList().range(PROCESSING_KEY, -limit, -1);
        return ids == null ? List.of() : ids;
    }
    public long readySize() { Long size = redis.opsForList().size(READY_KEY); return size == null ? 0 : size; }
    public long processingSize() { Long size = redis.opsForList().size(PROCESSING_KEY); return size == null ? 0 : size; }
}

