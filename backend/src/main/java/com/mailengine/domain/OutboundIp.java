package com.mailengine.domain;

import java.time.Instant;
import java.util.UUID;

public record OutboundIp(
        UUID id,
        UUID tenantId,
        UUID ipPoolId,
        String publicIpAddress,
        String elasticAllocationId,
        String reverseDnsName,
        String status,
        Instant createdAt
) {
}
