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
        Instant createdAt
) {
}
