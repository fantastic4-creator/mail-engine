package com.mailengine.data.repository;

import com.mailengine.data.entity.IpPoolEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IpPoolRepository extends JpaRepository<IpPoolEntity, UUID> {

    Optional<IpPoolEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<IpPoolEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
