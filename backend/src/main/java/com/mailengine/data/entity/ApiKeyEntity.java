package com.mailengine.data.entity;

import com.mailengine.domain.ApiKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_key")
public class ApiKeyEntity {

    @Id
    UUID id;

    @Column(nullable = false)
    UUID tenantId;

    @Column(nullable = false)
    String name;

    @Column(nullable = false, unique = true)
    String keyHash;

    @Column(nullable = false)
    String keyPrefix;

    Instant lastUsedAt;

    @Column(nullable = false)
    Instant createdAt;

    protected ApiKeyEntity() {}

    public static ApiKeyEntity from(ApiKey k) {
        ApiKeyEntity e = new ApiKeyEntity();
        e.id = k.id();
        e.tenantId = k.tenantId();
        e.name = k.name();
        e.keyHash = k.keyHash();
        e.keyPrefix = k.keyPrefix();
        e.lastUsedAt = k.lastUsedAt();
        e.createdAt = k.createdAt();
        return e;
    }

    public ApiKey toDomain() {
        return new ApiKey(id, tenantId, name, keyHash, keyPrefix, lastUsedAt, createdAt);
    }
}
