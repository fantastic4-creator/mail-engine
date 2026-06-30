package com.mailengine.api.dto;

public record SesNotificationMessage(
        String notificationType,
        SesBounce bounce,
        SesComplaint complaint
) {}
