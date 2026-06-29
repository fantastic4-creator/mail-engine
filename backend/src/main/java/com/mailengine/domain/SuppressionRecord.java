package com.mailengine.domain;

import java.time.Instant;
import java.util.UUID;

public record SuppressionRecord(
        UUID id,
        UUID tenantId,
        String email,
        String reason,
        Instant createdAt
) {
}
