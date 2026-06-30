package com.mailengine.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailengine.api.dto.SesBounce;
import com.mailengine.api.dto.SesComplaint;
import com.mailengine.api.dto.SesNotificationMessage;
import com.mailengine.api.dto.SesRecipient;
import com.mailengine.api.dto.SnsEnvelope;
import com.mailengine.data.PlatformStateStore;
import com.mailengine.domain.SuppressionRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class BounceWebhookController {

    private final PlatformStateStore store;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public BounceWebhookController(PlatformStateStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    @PostMapping("/webhooks/{tenantId}/ses")
    public ResponseEntity<Void> handleSes(
            @PathVariable UUID tenantId,
            @RequestBody SnsEnvelope envelope) {
        if ("SubscriptionConfirmation".equals(envelope.Type())) {
            restTemplate.getForObject(envelope.SubscribeURL(), String.class);
            return ResponseEntity.ok().build();
        }
        if ("Notification".equals(envelope.Type())) {
            try {
                SesNotificationMessage notification = objectMapper.readValue(
                        envelope.Message(), SesNotificationMessage.class);
                if ("Bounce".equals(notification.notificationType())
                        && notification.bounce() != null
                        && "Permanent".equals(notification.bounce().bounceType())) {
                    List<SesRecipient> recipients = notification.bounce().bouncedRecipients();
                    if (recipients != null) {
                        for (SesRecipient recipient : recipients) {
                            suppressEmail(tenantId, recipient.emailAddress(), "Permanent bounce");
                        }
                    }
                } else if ("Complaint".equals(notification.notificationType())
                        && notification.complaint() != null) {
                    List<SesRecipient> recipients = notification.complaint().complainedRecipients();
                    if (recipients != null) {
                        for (SesRecipient recipient : recipients) {
                            suppressEmail(tenantId, recipient.emailAddress(), "Complaint");
                        }
                    }
                }
            } catch (JsonProcessingException e) {
                // ignore malformed notification payload
            }
        }
        return ResponseEntity.ok().build();
    }

    private void suppressEmail(UUID tenantId, String email, String reason) {
        store.saveSuppression(new SuppressionRecord(
                UUID.randomUUID(), tenantId, email, reason, Instant.now()));
    }
}
