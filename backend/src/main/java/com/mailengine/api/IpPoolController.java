package com.mailengine.api;

import com.mailengine.api.dto.CreateIpPoolRequest;
import com.mailengine.api.dto.CreateOutboundIpRequest;
import com.mailengine.api.dto.IpPoolResponse;
import com.mailengine.api.dto.OutboundIpResponse;
import com.mailengine.service.IpPoolService;
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
@RequestMapping("/api/tenants/{tenantId}/ip-pools")
public class IpPoolController {

    private final IpPoolService ipPoolService;

    public IpPoolController(IpPoolService ipPoolService) {
        this.ipPoolService = ipPoolService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IpPoolResponse createIpPool(@PathVariable UUID tenantId, @Valid @RequestBody CreateIpPoolRequest request) {
        return ipPoolService.createIpPool(tenantId, request);
    }

    @GetMapping
    public List<IpPoolResponse> listIpPools(@PathVariable UUID tenantId) {
        return ipPoolService.listIpPools(tenantId);
    }

    @PostMapping("/{ipPoolId}/ips")
    @ResponseStatus(HttpStatus.CREATED)
    public OutboundIpResponse addOutboundIp(
            @PathVariable UUID tenantId,
            @PathVariable UUID ipPoolId,
            @Valid @RequestBody CreateOutboundIpRequest request
    ) {
        return ipPoolService.addOutboundIp(tenantId, ipPoolId, request);
    }

    @GetMapping("/{ipPoolId}/ips")
    public List<OutboundIpResponse> listOutboundIps(@PathVariable UUID tenantId, @PathVariable UUID ipPoolId) {
        return ipPoolService.listOutboundIps(tenantId, ipPoolId);
    }
}
