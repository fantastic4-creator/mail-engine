package com.mailengine.api;

import com.mailengine.api.dto.CreateSuppressionRequest;
import com.mailengine.api.dto.SuppressionResponse;
import com.mailengine.service.SuppressionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}/suppressions")
public class SuppressionController {

    private final SuppressionService suppressionService;

    public SuppressionController(SuppressionService suppressionService) {
        this.suppressionService = suppressionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuppressionResponse createSuppression(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateSuppressionRequest request
    ) {
        return suppressionService.createSuppression(tenantId, request);
    }

    @GetMapping
    public List<SuppressionResponse> listSuppressions(@PathVariable UUID tenantId) {
        return suppressionService.listSuppressions(tenantId);
    }
}
