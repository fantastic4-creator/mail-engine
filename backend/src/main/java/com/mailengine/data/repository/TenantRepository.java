package com.mailengine.data.repository;

import com.mailengine.data.entity.TenantEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {

    List<TenantEntity> findAllByOrderByCreatedAtDesc();
}
