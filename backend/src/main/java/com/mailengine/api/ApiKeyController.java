package com.mailengine.api;

import com.mailengine.api.dto.ApiKeyCreatedResponse;
import com.mailengine.api.dto.ApiKeyResponse;
import com.mailengine.api.dto.CreateApiKeyRequest;
import com.mailengine.data.PlatformStateStore;
import com.mailengine.service.ApiKeyService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final PlatformStateStore store;

    public ApiKeyController(ApiKeyService apiKeyService, PlatformStateStore store) {
        this.apiKeyService = apiKeyService;
        this.store = store;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyCreatedResponse createApiKey(@PathVariable UUID tenantId, @Valid @RequestBody CreateApiKeyRequest request) {
        return apiKeyService.generate(tenantId, request.name());
    }

    @GetMapping
    public List<ApiKeyResponse> listApiKeys(@PathVariable UUID tenantId) {
        return store.listApiKeys(tenantId).stream()
                .map(k -> new ApiKeyResponse(k.id(), k.tenantId(), k.name(), k.keyPrefix(), k.lastUsedAt(), k.createdAt()))
                .toList();
    }

    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteApiKey(@PathVariable UUID tenantId, @PathVariable UUID keyId) {
        store.deleteApiKey(keyId);
    }
}
