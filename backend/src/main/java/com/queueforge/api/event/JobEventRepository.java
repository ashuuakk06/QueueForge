package com.queueforge.api.event;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobEventRepository extends JpaRepository<JobEvent, UUID> {
    List<JobEvent> findTop50ByJobIdAndUserIdOrderByOccurredAtDesc(UUID jobId, UUID userId);
}

