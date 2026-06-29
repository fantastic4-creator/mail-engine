package com.mailengine.data.entity;

import com.mailengine.domain.DomainVerificationStatus;
import com.mailengine.domain.SendingDomain;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sending_domain")
public class SendingDomainEntity {

    @Id
    UUID id;

    @Column(nullable = false)
    UUID tenantId;

    @Column(nullable = false)
    String domainName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    DomainVerificationStatus verificationStatus;

    String verificationToken;
    String dkimSelector;

    @Column(columnDefinition = "text")
    String dkimPublicKey;

    @Column(columnDefinition = "text")
    String dkimPrivateKeyPem;

    @Column(nullable = false)
    Instant createdAt;

    Instant verifiedAt;

    protected SendingDomainEntity() {}

    public static SendingDomainEntity from(SendingDomain d) {
        SendingDomainEntity e = new SendingDomainEntity();
        e.id = d.id();
        e.tenantId = d.tenantId();
        e.domainName = d.domainName();
        e.verificationStatus = d.verificationStatus();
        e.verificationToken = d.verificationToken();
        e.dkimSelector = d.dkimSelector();
        e.dkimPublicKey = d.dkimPublicKey();
        e.dkimPrivateKeyPem = d.dkimPrivateKeyPem();
        e.createdAt = d.createdAt();
        e.verifiedAt = d.verifiedAt();
        return e;
    }

    public SendingDomain toDomain() {
        return new SendingDomain(id, tenantId, domainName, verificationStatus,
                verificationToken, dkimSelector, dkimPublicKey, dkimPrivateKeyPem,
                createdAt, verifiedAt);
    }
}
