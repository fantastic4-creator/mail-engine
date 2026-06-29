package com.mailengine.service;

import com.mailengine.api.dto.CreateIpPoolRequest;
import com.mailengine.api.dto.CreateOutboundIpRequest;
import com.mailengine.api.dto.IpPoolResponse;
import com.mailengine.api.dto.OutboundIpResponse;
import com.mailengine.data.PlatformStateStore;
import com.mailengine.domain.IpPool;
import com.mailengine.domain.OutboundIp;
import com.mailengine.domain.Tenant;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IpPoolService {

    private final PlatformStateStore store;

    public IpPoolService(PlatformStateStore store) {
        this.store = store;
    }

    public IpPoolResponse createIpPool(UUID tenantId, CreateIpPoolRequest request) {
        Tenant tenant = requireTenant(tenantId);
        IpPool ipPool = new IpPool(
                UUID.randomUUID(),
                tenant.id(),
                request.name().trim(),
                request.trafficType().trim().toUpperCase(Locale.ROOT),
                Instant.now()
        );
        store.saveIpPool(ipPool);
        return toResponse(ipPool);
    }

    public List<IpPoolResponse> listIpPools(UUID tenantId) {
        requireTenant(tenantId);
        return store.listIpPools(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    public OutboundIpResponse addOutboundIp(UUID tenantId, UUID ipPoolId, CreateOutboundIpRequest request) {
        requireTenant(tenantId);
        IpPool ipPool = requireIpPool(tenantId, ipPoolId);
        OutboundIp outboundIp = new OutboundIp(
                UUID.randomUUID(),
                tenantId,
                ipPool.id(),
                request.publicIpAddress().trim(),
                request.elasticAllocationId().trim(),
                request.reverseDnsName().trim().toLowerCase(Locale.ROOT),
                "ACTIVE",
                Instant.now()
        );
        store.saveOutboundIp(outboundIp);
        return toResponse(outboundIp);
    }

    public List<OutboundIpResponse> listOutboundIps(UUID tenantId, UUID ipPoolId) {
        requireTenant(tenantId);
        requireIpPool(tenantId, ipPoolId);
        return store.listOutboundIps(tenantId, ipPoolId).stream()
                .map(this::toResponse)
                .toList();
    }

    private Tenant requireTenant(UUID tenantId) {
        return store.findTenant(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    private IpPool requireIpPool(UUID tenantId, UUID ipPoolId) {
        return store.findIpPoolForTenant(tenantId, ipPoolId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "IP pool not found for tenant"));
    }

    private IpPoolResponse toResponse(IpPool ipPool) {
        long outboundIpCount = store.listOutboundIps(ipPool.tenantId(), ipPool.id()).size();
        return new IpPoolResponse(
                ipPool.id(),
                ipPool.tenantId(),
                ipPool.name(),
                ipPool.trafficType(),
                ipPool.createdAt(),
                outboundIpCount
        );
    }

    private OutboundIpResponse toResponse(OutboundIp outboundIp) {
        return new OutboundIpResponse(
                outboundIp.id(),
                outboundIp.tenantId(),
                outboundIp.ipPoolId(),
                outboundIp.publicIpAddress(),
                outboundIp.elasticAllocationId(),
                outboundIp.reverseDnsName(),
                outboundIp.status(),
                outboundIp.createdAt()
        );
    }
}
