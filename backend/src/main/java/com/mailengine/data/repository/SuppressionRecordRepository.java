package com.mailengine.data.repository;

import com.mailengine.data.entity.SuppressionRecordEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SuppressionRecordRepository extends JpaRepository<SuppressionRecordEntity, UUID> {

    List<SuppressionRecordEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    @Query("SELECT COUNT(s) > 0 FROM SuppressionRecordEntity s WHERE s.tenantId = :tenantId AND LOWER(s.email) = LOWER(:email)")
    boolean existsByTenantIdAndEmailIgnoreCase(@Param("tenantId") UUID tenantId, @Param("email") String email);
}
