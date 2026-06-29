package com.mailengine.api.dto;

import java.time.Instant;
import java.util.UUID;

public record OutboundMessageResponse(
        UUID id,
        UUID messageJobId,
        UUID campaignId,
        UUID tenantId,
        UUID domainId,
        UUID ipPoolId,
        UUID outboundIpId,
        String outboundIpAddress,
        String recipientEmail,
        String subject,
        String deliveryStatus,
        Instant sentAt
) {
}
