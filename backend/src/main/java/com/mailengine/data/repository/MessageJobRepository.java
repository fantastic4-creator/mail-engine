package com.mailengine.data.repository;

import com.mailengine.data.entity.MessageJobEntity;
import com.mailengine.domain.MessageJobStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface MessageJobRepository extends JpaRepository<MessageJobEntity, UUID> {

    List<MessageJobEntity> findByCampaignIdOrderByCreatedAt(UUID campaignId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT j FROM MessageJobEntity j WHERE j.campaignId = :campaignId AND j.status = :status AND j.scheduledAt <= :now ORDER BY j.createdAt")
    List<MessageJobEntity> lockPendingJobs(
            @Param("campaignId") UUID campaignId,
            @Param("now") Instant now,
            @Param("status") MessageJobStatus status,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT j FROM MessageJobEntity j WHERE j.status = :status AND j.nextRetryAt <= :now ORDER BY j.nextRetryAt")
    List<MessageJobEntity> lockRetryJobs(
            @Param("status") MessageJobStatus status,
            @Param("now") Instant now,
            Pageable pageable);

    @Modifying
    @Query("UPDATE MessageJobEntity j SET j.status = :status, j.claimedAt = :claimedAt WHERE j.id IN :ids")
    void markClaimed(
            @Param("ids") List<UUID> ids,
            @Param("status") MessageJobStatus status,
            @Param("claimedAt") Instant claimedAt);

    @Modifying
    @Query("UPDATE MessageJobEntity j SET j.status = :pending, j.claimedAt = null, j.nextRetryAt = null WHERE j.id IN :ids")
    void resetRetryToPending(
            @Param("ids") List<UUID> ids,
            @Param("pending") MessageJobStatus pending);

    @Modifying
    @Query("UPDATE MessageJobEntity j SET j.status = :newStatus, j.completedAt = :now, j.lastError = :reason WHERE j.campaignId = :campaignId AND j.status IN :statuses")
    int cancelActiveJobs(
            @Param("campaignId") UUID campaignId,
            @Param("statuses") List<MessageJobStatus> statuses,
            @Param("newStatus") MessageJobStatus newStatus,
            @Param("now") Instant now,
            @Param("reason") String reason);
}
