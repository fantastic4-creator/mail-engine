package com.mailengine.data.repository;

import com.mailengine.data.entity.RecipientEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipientRepository extends JpaRepository<RecipientEntity, UUID> {

    List<RecipientEntity> findByCampaignIdOrderByCreatedAt(UUID campaignId);
}
