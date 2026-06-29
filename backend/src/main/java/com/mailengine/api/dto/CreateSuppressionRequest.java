package com.mailengine.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSuppressionRequest(
        @NotBlank(message = "email is required")
        String email,

        @NotBlank(message = "reason is required")
        String reason
) {
}
