package com.mailengine.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateOutboundIpRequest(
        @NotBlank(message = "publicIpAddress is required")
        String publicIpAddress,

        @NotBlank(message = "elasticAllocationId is required")
        String elasticAllocationId,

        @NotBlank(message = "reverseDnsName is required")
        String reverseDnsName
) {
}
