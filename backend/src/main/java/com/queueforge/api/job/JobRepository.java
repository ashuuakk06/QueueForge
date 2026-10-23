package com.queueforge.api.job;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, UUID> {
    List<Job> findTop500ByOwner_IdOrderByCreatedAtDesc(UUID ownerId);
    Optional<Job> findByIdAndOwner_Id(UUID id, UUID ownerId);
    long countByOwner_Id(UUID ownerId);
    long countByOwner_IdAndStatus(UUID ownerId, JobStatus status);
    long countByOwner_IdAndCreatedAtAfter(UUID ownerId, Instant createdAt);
    List<Job> findTop100ByStatusInAndAvailableAtLessThanEqualOrderByPriorityDescCreatedAtAsc(
            Collection<JobStatus> statuses, Instant availableAt);
    List<Job> findByStatusAndLockedAtBefore(JobStatus status, Instant lockedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Job j where j.id = :id")
    Optional<Job> lockById(@Param("id") UUID id);
}

