package com.mailengine.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateCampaignRequest(
        @NotNull
        UUID tenantId,

        @NotNull
        UUID domainId,

        @NotBlank(message = "name is required")
        String name,

        @NotBlank(message = "subject is required")
        String subject,

        @NotBlank(message = "body is required")
        String body,

        String recipientEmail,

        List<@NotBlank(message = "recipient email cannot be blank") String> recipientEmails,

        Integer maxSendsPerHour
) {
}
