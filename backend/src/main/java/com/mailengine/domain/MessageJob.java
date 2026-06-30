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
        int retryCount,
        Instant nextRetryAt,
        Instant createdAt
) {
    public MessageJob claim(Instant claimedAt) {
        return new MessageJob(
                id, campaignId, tenantId, domainId, recipientId, recipientEmail,
                MessageJobStatus.CLAIMED, scheduledAt, claimedAt, completedAt,
                lastError, retryCount, nextRetryAt, createdAt
        );
    }

    public MessageJob complete(MessageJobStatus status, Instant completedAt, String lastError) {
        return new MessageJob(
                id, campaignId, tenantId, domainId, recipientId, recipientEmail,
                status, scheduledAt, claimedAt, completedAt, lastError,
                retryCount, nextRetryAt, createdAt
        );
    }

    public MessageJob scheduleRetry(Instant nextRetryAt) {
        return new MessageJob(
                id, campaignId, tenantId, domainId, recipientId, recipientEmail,
                MessageJobStatus.RETRY_SCHEDULED, scheduledAt, claimedAt, completedAt,
                lastError, retryCount + 1, nextRetryAt, createdAt
        );
    }
}
