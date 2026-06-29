package com.mailengine.domain;

import java.time.Instant;
import java.util.UUID;

public record SendingDomain(
        UUID id,
        UUID tenantId,
        String domainName,
        DomainVerificationStatus verificationStatus,
        String verificationToken,
        String dkimSelector,
        String dkimPublicKey,
        String dkimPrivateKeyPem,
        Instant createdAt,
        Instant verifiedAt
) {
    public SendingDomain withVerificationStatus(DomainVerificationStatus status, Instant verifiedAt) {
        return new SendingDomain(
                id,
                tenantId,
                domainName,
                status,
                verificationToken,
                dkimSelector,
                dkimPublicKey,
                dkimPrivateKeyPem,
                createdAt,
                verifiedAt
        );
    }
}
