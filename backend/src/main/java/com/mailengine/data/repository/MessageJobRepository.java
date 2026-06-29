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

    @Modifying
    @Query("UPDATE MessageJobEntity j SET j.status = :status, j.claimedAt = :claimedAt WHERE j.id IN :ids")
    void markClaimed(
            @Param("ids") List<UUID> ids,
            @Param("status") MessageJobStatus status,
            @Param("claimedAt") Instant claimedAt);
}
