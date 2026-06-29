package com.mailengine.api.dto;

import java.time.Instant;
import java.util.UUID;

public record DomainResponse(
        UUID id,
        UUID tenantId,
        String domainName,
        String verificationStatus,
        String verificationToken,
        String dkimSelector,
        Instant createdAt,
        Instant verifiedAt
) {
}
