package com.mailengine.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SuppressionResponse(
        UUID id,
        UUID tenantId,
        String email,
        String reason,
        Instant createdAt
) {
}
