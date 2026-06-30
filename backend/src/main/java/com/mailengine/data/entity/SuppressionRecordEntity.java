package com.mailengine.data.entity;

import com.mailengine.domain.SuppressionRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suppression_record")
public class SuppressionRecordEntity {

    @Id
    UUID id;

    @Column(nullable = false)
    UUID tenantId;

    @Column(nullable = false)
    String email;

    String reason;

    @Column(nullable = false)
    Instant createdAt;

    protected SuppressionRecordEntity() {}

    public static SuppressionRecordEntity from(SuppressionRecord s) {
        SuppressionRecordEntity e = new SuppressionRecordEntity();
        e.id = s.id();
        e.tenantId = s.tenantId();
        e.email = s.email();
        e.reason = s.reason();
        e.createdAt = s.createdAt();
        return e;
    }

    public SuppressionRecord toDomain() {
        return new SuppressionRecord(id, tenantId, email, reason, createdAt);
    }
}
