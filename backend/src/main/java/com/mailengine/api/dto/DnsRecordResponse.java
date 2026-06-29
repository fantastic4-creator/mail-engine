package com.mailengine.api.dto;

public record DnsRecordResponse(
        String type,
        String name,
        String value,
        String purpose
) {
}
