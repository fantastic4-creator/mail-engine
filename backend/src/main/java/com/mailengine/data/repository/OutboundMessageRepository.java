package com.mailengine.data.repository;

import com.mailengine.data.entity.OutboundMessageEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboundMessageRepository extends JpaRepository<OutboundMessageEntity, UUID> {

    List<OutboundMessageEntity> findAllByOrderBySentAtDesc();
}
