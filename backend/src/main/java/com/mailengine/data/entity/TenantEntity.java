package com.mailengine.data.entity;

import com.mailengine.domain.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant")
public class TenantEntity {

    @Id
    UUID id;

    @Column(nullable = false)
    String name;

    @Column(nullable = false)
    Instant createdAt;

    protected TenantEntity() {}

    public static TenantEntity from(Tenant t) {
        TenantEntity e = new TenantEntity();
        e.id = t.id();
        e.name = t.name();
        e.createdAt = t.createdAt();
        return e;
    }

    public Tenant toDomain() {
        return new Tenant(id, name, createdAt);
    }
}
