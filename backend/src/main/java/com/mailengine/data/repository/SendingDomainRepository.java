package com.mailengine.data.repository;

import com.mailengine.data.entity.SendingDomainEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SendingDomainRepository extends JpaRepository<SendingDomainEntity, UUID> {

    List<SendingDomainEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<SendingDomainEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
