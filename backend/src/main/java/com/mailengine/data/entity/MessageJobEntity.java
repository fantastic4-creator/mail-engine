package com.mailengine.data.entity;

import com.mailengine.domain.MessageJob;
import com.mailengine.domain.MessageJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_job")
public class MessageJobEntity {

    @Id
    UUID id;

    @Column(nullable = false)
    UUID campaignId;

    @Column(nullable = false)
    UUID tenantId;

    @Column(nullable = false)
    UUID domainId;

    @Column(nullable = false)
    UUID recipientId;

    @Column(nullable = false)
    String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    MessageJobStatus status;

    @Column(nullable = false)
    Instant scheduledAt;

    Instant claimedAt;
    Instant completedAt;

    @Column(columnDefinition = "text")
    String lastError;

    @Column(nullable = false)
    Instant createdAt;

    protected MessageJobEntity() {}

    public static MessageJobEntity from(MessageJob j) {
        MessageJobEntity e = new MessageJobEntity();
        e.id = j.id();
        e.campaignId = j.campaignId();
        e.tenantId = j.tenantId();
        e.domainId = j.domainId();
        e.recipientId = j.recipientId();
        e.recipientEmail = j.recipientEmail();
        e.status = j.status();
        e.scheduledAt = j.scheduledAt();
        e.claimedAt = j.claimedAt();
        e.completedAt = j.completedAt();
        e.lastError = j.lastError();
        e.createdAt = j.createdAt();
        return e;
    }

    public UUID getId() { return id; }

    public MessageJob toDomain() {
        return new MessageJob(id, campaignId, tenantId, domainId, recipientId,
                recipientEmail, status, scheduledAt, claimedAt, completedAt, lastError, createdAt);
    }
}
