package com.mailengine.data.repository;

import com.mailengine.data.entity.CampaignEntity;
import com.mailengine.domain.MessageJobStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignRepository extends JpaRepository<CampaignEntity, UUID> {

    List<CampaignEntity> findAllByOrderByCreatedAtDesc();

    @Query("SELECT DISTINCT c FROM CampaignEntity c JOIN MessageJobEntity j ON j.campaignId = c.id WHERE j.status = :status")
    List<CampaignEntity> findCampaignsWithJobStatus(@Param("status") MessageJobStatus status);
}
