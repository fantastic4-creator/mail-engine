package com.mailengine.data.entity;

import com.mailengine.domain.Recipient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recipient")
public class RecipientEntity {

    @Id
    UUID id;

    @Column(nullable = false)
    UUID tenantId;

    @Column(nullable = false)
    UUID campaignId;

    @Column(nullable = false)
    String email;

    @Column(nullable = false)
    Instant createdAt;

    protected RecipientEntity() {}

    public static RecipientEntity from(Recipient r) {
        RecipientEntity e = new RecipientEntity();
        e.id = r.id();
        e.tenantId = r.tenantId();
        e.campaignId = r.campaignId();
        e.email = r.email();
        e.createdAt = r.createdAt();
        return e;
    }

    public Recipient toDomain() {
        return new Recipient(id, tenantId, campaignId, email, createdAt);
    }
}
