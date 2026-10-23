package com.queueforge.api.event;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface JobEventRepository extends MongoRepository<JobEventDocument, String> {
    List<JobEventDocument> findTop50ByJobIdAndUserIdOrderByOccurredAtDesc(String jobId, String userId);
}

