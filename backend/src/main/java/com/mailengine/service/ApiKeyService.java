package com.mailengine.service;

import com.mailengine.api.dto.ApiKeyCreatedResponse;
import com.mailengine.data.PlatformStateStore;
import com.mailengine.domain.ApiKey;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyService {

    private final PlatformStateStore store;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(PlatformStateStore store) {
        this.store = store;
    }

    public ApiKeyCreatedResponse generate(UUID tenantId, String name) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawKey = "me_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = sha256Hex(rawKey);
        ApiKey apiKey = new ApiKey(UUID.randomUUID(), tenantId, name, hash, rawKey.substring(0, 10), null, Instant.now());
        store.saveApiKey(apiKey);
        return new ApiKeyCreatedResponse(apiKey.id(), apiKey.tenantId(), apiKey.name(), apiKey.keyPrefix(), rawKey, apiKey.createdAt());
    }

    public Optional<UUID> validate(String rawKey) {
        String hash = sha256Hex(rawKey);
        return store.findApiKeyByHash(hash).map(key -> {
            store.touchApiKeyLastUsed(key.id());
            return key.tenantId();
        });
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
