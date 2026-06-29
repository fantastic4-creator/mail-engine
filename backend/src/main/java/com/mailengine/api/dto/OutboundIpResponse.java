package com.mailengine.api.dto;

import java.time.Instant;
import java.util.UUID;

public record OutboundIpResponse(
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
