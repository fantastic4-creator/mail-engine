package com.mailengine.data.repository;

import com.mailengine.data.entity.OutboundIpEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboundIpRepository extends JpaRepository<OutboundIpEntity, UUID> {

    List<OutboundIpEntity> findByTenantIdAndIpPoolIdOrderByCreatedAtDesc(UUID tenantId, UUID ipPoolId);

    Optional<OutboundIpEntity> findFirstByTenantIdAndStatusOrderByCreatedAt(UUID tenantId, String status);
}
