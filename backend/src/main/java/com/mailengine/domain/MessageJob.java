package com.mailengine.domain;

import java.time.Instant;
import java.util.UUID;

public record MessageJob(
        UUID id,
        UUID campaignId,
        UUID tenantId,
        UUID domainId,
        UUID recipientId,
        String recipientEmail,
        MessageJobStatus status,
        Instant scheduledAt,
        Instant claimedAt,
        Instant completedAt,
        String lastError,
        Instant createdAt
) {
    public MessageJob claim(Instant claimedAt) {
        return new MessageJob(
                id,
                campaignId,
                tenantId,
                domainId,
                recipientId,
                recipientEmail,
                MessageJobStatus.CLAIMED,
                scheduledAt,
                claimedAt,
                completedAt,
                lastError,
                createdAt
        );
    }

    public MessageJob complete(MessageJobStatus status, Instant completedAt, String lastError) {
        return new MessageJob(
                id,
                campaignId,
                tenantId,
                domainId,
                recipientId,
                recipientEmail,
                status,
                scheduledAt,
                claimedAt,
                completedAt,
                lastError,
                createdAt
        );
    }
}
