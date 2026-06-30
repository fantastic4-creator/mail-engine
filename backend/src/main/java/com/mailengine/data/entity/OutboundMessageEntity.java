package com.mailengine.data.entity;

import com.mailengine.domain.OutboundMessage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbound_message")
public class OutboundMessageEntity {

    @Id
    UUID id;

    UUID messageJobId;
    UUID campaignId;
    UUID tenantId;
    UUID domainId;
    UUID ipPoolId;
    UUID outboundIpId;
    String outboundIpAddress;
    String recipientEmail;
    String subject;
    String deliveryStatus;

    @Column(nullable = false)
    Instant sentAt;

    protected OutboundMessageEntity() {}

    public static OutboundMessageEntity from(OutboundMessage m) {
        OutboundMessageEntity e = new OutboundMessageEntity();
        e.id = m.id();
        e.messageJobId = m.messageJobId();
        e.campaignId = m.campaignId();
        e.tenantId = m.tenantId();
        e.domainId = m.domainId();
        e.ipPoolId = m.ipPoolId();
        e.outboundIpId = m.outboundIpId();
        e.outboundIpAddress = m.outboundIpAddress();
        e.recipientEmail = m.recipientEmail();
        e.subject = m.subject();
        e.deliveryStatus = m.deliveryStatus();
        e.sentAt = m.sentAt();
        return e;
    }

    public OutboundMessage toDomain() {
        return new OutboundMessage(id, messageJobId, campaignId, tenantId, domainId,
                ipPoolId, outboundIpId, outboundIpAddress, recipientEmail, subject,
                deliveryStatus, sentAt);
    }
}
