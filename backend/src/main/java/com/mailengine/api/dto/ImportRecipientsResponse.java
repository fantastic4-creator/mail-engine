package com.mailengine.api.dto;

public record ImportRecipientsResponse(int imported, int skipped, int totalInCampaign) {
}
