package com.mailengine.api.dto;

import java.time.Instant;
import java.util.UUID;

public record CampaignResponse(
        UUID id,
        UUID tenantId,
        UUID domainId,
        String name,
        String subject,
        int recipientCount,
        int messageJobCount,
        int maxSendsPerHour,
        String status,
        Instant createdAt
) {
}
