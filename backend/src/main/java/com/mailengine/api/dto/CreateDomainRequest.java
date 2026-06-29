package com.mailengine.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDomainRequest(
        @NotBlank(message = "domainName is required")
        String domainName
) {
}
