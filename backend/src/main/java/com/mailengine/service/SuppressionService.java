package com.mailengine.service;

import com.mailengine.api.dto.CreateSuppressionRequest;
import com.mailengine.api.dto.SuppressionResponse;
import com.mailengine.data.PlatformStateStore;
import com.mailengine.domain.SuppressionRecord;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SuppressionService {

    private final PlatformStateStore store;

    public SuppressionService(PlatformStateStore store) {
        this.store = store;
    }

    public SuppressionResponse createSuppression(UUID tenantId, CreateSuppressionRequest request) {
        store.findTenant(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        SuppressionRecord suppressionRecord = new SuppressionRecord(
                UUID.randomUUID(),
                tenantId,
                normalizeEmail(request.email()),
                request.reason().trim().toUpperCase(Locale.ROOT),
                Instant.now()
        );
        return toResponse(store.saveSuppression(suppressionRecord));
    }

    public List<SuppressionResponse> listSuppressions(UUID tenantId) {
        store.findTenant(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        return store.listSuppressions(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    private SuppressionResponse toResponse(SuppressionRecord suppressionRecord) {
        return new SuppressionResponse(
                suppressionRecord.id(),
                suppressionRecord.tenantId(),
                suppressionRecord.email(),
                suppressionRecord.reason(),
                suppressionRecord.createdAt()
        );
    }

    private String normalizeEmail(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email: " + email);
        }
        return normalized;
    }
}
