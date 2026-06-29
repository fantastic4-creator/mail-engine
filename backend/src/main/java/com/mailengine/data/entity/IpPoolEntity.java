package com.mailengine.data.entity;

import com.mailengine.domain.IpPool;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ip_pool")
public class IpPoolEntity {

    @Id
    UUID id;

    @Column(nullable = false)
    UUID tenantId;

    @Column(nullable = false)
    String name;

    @Column(nullable = false)
    String trafficType;

    @Column(nullable = false)
    Instant createdAt;

    protected IpPoolEntity() {}

    public static IpPoolEntity from(IpPool p) {
        IpPoolEntity e = new IpPoolEntity();
        e.id = p.id();
        e.tenantId = p.tenantId();
        e.name = p.name();
        e.trafficType = p.trafficType();
        e.createdAt = p.createdAt();
        return e;
    }

    public IpPool toDomain() {
        return new IpPool(id, tenantId, name, trafficType, createdAt);
    }
}
