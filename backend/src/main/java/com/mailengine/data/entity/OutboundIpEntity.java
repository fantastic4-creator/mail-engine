package com.mailengine.data.entity;

import com.mailengine.domain.OutboundIp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbound_ip")
public class OutboundIpEntity {

    @Id
    UUID id;

    @Column(nullable = false)
    UUID tenantId;

    @Column(nullable = false)
    UUID ipPoolId;

    @Column(nullable = false)
    String publicIpAddress;

    String elasticAllocationId;
    String reverseDnsName;

    @Column(nullable = false)
    String status;

    @Column(nullable = false)
    Instant createdAt;

    protected OutboundIpEntity() {}

    public static OutboundIpEntity from(OutboundIp ip) {
        OutboundIpEntity e = new OutboundIpEntity();
        e.id = ip.id();
        e.tenantId = ip.tenantId();
        e.ipPoolId = ip.ipPoolId();
        e.publicIpAddress = ip.publicIpAddress();
        e.elasticAllocationId = ip.elasticAllocationId();
        e.reverseDnsName = ip.reverseDnsName();
        e.status = ip.status();
        e.createdAt = ip.createdAt();
        return e;
    }

    public OutboundIp toDomain() {
        return new OutboundIp(id, tenantId, ipPoolId, publicIpAddress,
                elasticAllocationId, reverseDnsName, status, createdAt);
    }
}
