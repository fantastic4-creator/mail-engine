package com.mailengine.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyCreatedResponse(UUID id, UUID tenantId, String name, String keyPrefix, String rawKey, Instant createdAt) {}
