package com.mailengine.data.entity;

import com.mailengine.domain.Campaign;
import com.mailengine.domain.CampaignStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "campaign")
public class CampaignEntity {

    @Id
    UUID id;

    @Column(nullable = false)
    UUID tenantId;

    @Column(nullable = false)
    UUID domainId;

    @Column(nullable = false)
    String name;

    @Column(nullable = false)
    String subject;

    @Column(nullable = false, columnDefinition = "text")
    String body;

    @Column(nullable = false)
    int recipientCount;

    @Column(nullable = false)
    int maxSendsPerHour;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    CampaignStatus status;

    @Column(nullable = false)
    Instant createdAt;

    protected CampaignEntity() {}

    public static CampaignEntity from(Campaign c) {
        CampaignEntity e = new CampaignEntity();
        e.id = c.id();
        e.tenantId = c.tenantId();
        e.domainId = c.domainId();
        e.name = c.name();
        e.subject = c.subject();
        e.body = c.body();
        e.recipientCount = c.recipientCount();
        e.maxSendsPerHour = c.maxSendsPerHour();
        e.status = c.status();
        e.createdAt = c.createdAt();
        return e;
    }

    public Campaign toDomain() {
        return new Campaign(id, tenantId, domainId, name, subject, body, recipientCount, maxSendsPerHour, status, createdAt);
    }
}
