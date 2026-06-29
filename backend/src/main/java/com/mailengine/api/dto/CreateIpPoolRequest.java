package com.mailengine.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateIpPoolRequest(
        @NotBlank(message = "name is required")
        String name,

        @NotBlank(message = "trafficType is required")
        String trafficType
) {
}
