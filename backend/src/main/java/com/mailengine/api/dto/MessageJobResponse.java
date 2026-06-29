package com.mailengine.api.dto;

import java.time.Instant;
import java.util.UUID;

public record MessageJobResponse(
        UUID id,
        UUID campaignId,
        UUID tenantId,
        UUID domainId,
        UUID recipientId,
        String recipientEmail,
        String status,
        Instant scheduledAt,
        Instant claimedAt,
        Instant completedAt,
        String lastError,
        Instant createdAt
) {
}
