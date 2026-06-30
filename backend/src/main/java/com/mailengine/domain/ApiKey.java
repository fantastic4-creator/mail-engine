package com.mailengine.domain;

import java.time.Instant;
import java.util.UUID;

public record ApiKey(UUID id, UUID tenantId, String name, String keyHash, String keyPrefix, Instant lastUsedAt, Instant createdAt) {}
