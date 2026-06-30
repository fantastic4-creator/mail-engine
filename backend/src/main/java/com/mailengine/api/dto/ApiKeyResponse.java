package com.mailengine.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyResponse(UUID id, UUID tenantId, String name, String keyPrefix, Instant lastUsedAt, Instant createdAt) {}
