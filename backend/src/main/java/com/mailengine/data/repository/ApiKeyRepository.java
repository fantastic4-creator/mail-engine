package com.mailengine.data.repository;

import com.mailengine.data.entity.ApiKeyEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, UUID> {

    Optional<ApiKeyEntity> findByKeyHash(String keyHash);

    List<ApiKeyEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    @Modifying
    @Query("UPDATE ApiKeyEntity k SET k.lastUsedAt = :now WHERE k.id = :id")
    void touchLastUsed(@Param("id") UUID id, @Param("now") Instant now);
}
