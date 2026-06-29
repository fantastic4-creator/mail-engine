package com.mailengine.api.dto;

import java.time.Instant;
import java.util.UUID;

public record IpPoolResponse(
        UUID id,
        UUID tenantId,
        String name,
        String trafficType,
        Instant createdAt,
        long outboundIpCount
) {
}
