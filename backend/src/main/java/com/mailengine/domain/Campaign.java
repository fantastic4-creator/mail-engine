package com.mailengine.domain;

import java.time.Instant;
import java.util.UUID;

public record Campaign(
        UUID id,
        UUID tenantId,
        UUID domainId,
        String name,
        String subject,
        String body,
        int recipientCount,
        int maxSendsPerHour,
        CampaignStatus status,
        Instant createdAt
) {
    public Campaign withStatus(CampaignStatus status) {
        return new Campaign(id, tenantId, domainId, name, subject, body, recipientCount, maxSendsPerHour, status, createdAt);
    }
}
