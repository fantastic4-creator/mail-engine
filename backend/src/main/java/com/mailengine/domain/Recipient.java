package com.mailengine.domain;

import java.time.Instant;
import java.util.UUID;

public record Recipient(
        UUID id,
        UUID tenantId,
        UUID campaignId,
        String email,
        Instant createdAt
) {
}
