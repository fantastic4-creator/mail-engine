package com.mailengine.data.repository;

import com.mailengine.data.entity.CampaignEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<CampaignEntity, UUID> {

    List<CampaignEntity> findAllByOrderByCreatedAtDesc();
}
