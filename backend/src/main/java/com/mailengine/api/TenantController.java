package com.mailengine.api;

import com.mailengine.api.dto.CreateDomainRequest;
import com.mailengine.api.dto.CreateTenantRequest;
import com.mailengine.api.dto.DnsRecordResponse;
import com.mailengine.api.dto.DomainResponse;
import com.mailengine.api.dto.TenantResponse;
import com.mailengine.service.TenantService;
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
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse createTenant(@Valid @RequestBody CreateTenantRequest request) {
        return tenantService.createTenant(request);
    }

    @GetMapping
    public List<TenantResponse> listTenants() {
        return tenantService.listTenants();
    }

    @GetMapping("/{tenantId}")
    public TenantResponse getTenant(@PathVariable UUID tenantId) {
        return tenantService.getTenant(tenantId);
    }

    @PostMapping("/{tenantId}/domains")
    @ResponseStatus(HttpStatus.CREATED)
    public DomainResponse addDomain(@PathVariable UUID tenantId, @Valid @RequestBody CreateDomainRequest request) {
        return tenantService.addDomain(tenantId, request);
    }

    @GetMapping("/{tenantId}/domains")
    public List<DomainResponse> listDomains(@PathVariable UUID tenantId) {
        return tenantService.listDomains(tenantId);
    }

    @GetMapping("/{tenantId}/domains/{domainId}/dns-records")
    public List<DnsRecordResponse> listDomainDnsRecords(@PathVariable UUID tenantId, @PathVariable UUID domainId) {
        return tenantService.listDomainDnsRecords(tenantId, domainId);
    }

    @PostMapping("/{tenantId}/domains/{domainId}/verify")
    public DomainResponse verifyDomain(@PathVariable UUID tenantId, @PathVariable UUID domainId) {
        return tenantService.verifyDomain(tenantId, domainId);
    }
}
