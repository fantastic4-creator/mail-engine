package com.mailengine.domain;

import java.time.Instant;
import java.util.UUID;

public record IpPool(
        UUID id,
        UUID tenantId,
        String name,
        String trafficType,
        Instant createdAt
) {
}
