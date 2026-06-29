package com.mailengine.api.dto;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String name,
        Instant createdAt,
        long domainCount
) {
}
