package com.mailengine.service;

import com.mailengine.api.dto.CampaignResponse;
import com.mailengine.api.dto.CreateCampaignRequest;
import com.mailengine.api.dto.ImportRecipientsResponse;
import com.mailengine.api.dto.MessageJobResponse;
import com.mailengine.api.dto.OutboundMessageResponse;
import com.mailengine.data.PlatformStateStore;
import com.mailengine.domain.Campaign;
import com.mailengine.domain.CampaignStatus;
import com.mailengine.domain.DomainVerificationStatus;
import com.mailengine.domain.MessageJob;
import com.mailengine.domain.MessageJobStatus;
import com.mailengine.domain.OutboundMessage;
import com.mailengine.domain.Recipient;
import com.mailengine.domain.SendingDomain;
import com.mailengine.domain.Tenant;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CampaignService {

    private final PlatformStateStore store;

    public CampaignService(PlatformStateStore store) {
        this.store = store;
    }

    public CampaignResponse createCampaign(CreateCampaignRequest request) {
        Tenant tenant = store.findTenant(request.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        SendingDomain domain = store.findDomainForTenant(tenant.id(), request.domainId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Domain not found for tenant"));

        if (domain.verificationStatus() != DomainVerificationStatus.VERIFIED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Domain must be verified before sending");
        }

        store.findFirstActiveOutboundIp(tenant.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant must have an active outbound IP before sending"));

        List<String> recipientEmails = normalizeRecipients(request);
        Instant now = Instant.now();
        Campaign campaign = new Campaign(
                UUID.randomUUID(),
                tenant.id(),
                domain.id(),
                request.name().trim(),
                request.subject().trim(),
                request.body(),
                recipientEmails.size(),
                CampaignStatus.SENDING,
                now
        );
        store.saveCampaign(campaign);

        recipientEmails.forEach(email -> {
            Recipient recipient = store.saveRecipient(new Recipient(
                    UUID.randomUUID(),
                    tenant.id(),
                    campaign.id(),
                    email,
                    now
            ));
            store.saveMessageJob(new MessageJob(
                    UUID.randomUUID(),
                    campaign.id(),
                    tenant.id(),
                    domain.id(),
                    recipient.id(),
                    email,
                    MessageJobStatus.PENDING,
                    now,
                    null,
                    null,
                    null,
                    0,
                    null,
                    now
            ));
        });

        return toResponse(campaign);
    }

    public ImportRecipientsResponse importRecipients(UUID campaignId, MultipartFile file) {
        Campaign campaign = store.findCampaign(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));

        store.findFirstActiveOutboundIp(campaign.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active outbound IP for tenant"));

        List<String> rawEmails;
        try {
            rawEmails = parseCsvEmails(file);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read file: " + e.getMessage());
        }

        Set<String> existingEmails = store.listRecipients(campaignId).stream()
                .map(Recipient::email)
                .collect(Collectors.toSet());

        List<String> newEmails = rawEmails.stream()
                .filter(e -> e.contains("@"))
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .filter(e -> !existingEmails.contains(e))
                .toList();

        int skipped = rawEmails.size() - newEmails.size();
        Instant now = Instant.now();

        newEmails.forEach(email -> {
            Recipient recipient = store.saveRecipient(
                    new Recipient(UUID.randomUUID(), campaign.tenantId(), campaignId, email, now));
            store.saveMessageJob(new MessageJob(
                    UUID.randomUUID(), campaignId, campaign.tenantId(), campaign.domainId(),
                    recipient.id(), email, MessageJobStatus.PENDING, now, null, null, null,
                    0, null, now));
        });

        if (!newEmails.isEmpty()) {
            Campaign updated = new Campaign(
                    campaign.id(), campaign.tenantId(), campaign.domainId(),
                    campaign.name(), campaign.subject(), campaign.body(),
                    campaign.recipientCount() + newEmails.size(), campaign.status(), campaign.createdAt());
            store.saveCampaign(updated);
        }

        return new ImportRecipientsResponse(newEmails.size(), skipped, existingEmails.size() + newEmails.size());
    }

    public List<CampaignResponse> listCampaigns() {
        return store.listCampaigns().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public CampaignResponse getCampaign(UUID campaignId) {
        Campaign campaign = store.findCampaign(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
        return toResponse(campaign);
    }

    public List<MessageJobResponse> listCampaignJobs(UUID campaignId) {
        store.findCampaign(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
        return store.listMessageJobs(campaignId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<OutboundMessageResponse> listOutbox() {
        return store.listOutboundMessages().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public CampaignResponse cancelCampaign(UUID campaignId) {
        Campaign campaign = store.findCampaign(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
        store.cancelCampaignJobs(campaignId);
        Campaign cancelled = store.saveCampaign(campaign.withStatus(CampaignStatus.FAILED));
        return toResponse(cancelled);
    }

    private List<String> parseCsvEmails(MultipartFile file) throws IOException {
        List<String> emails = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isBlank() || line.startsWith("#")) continue;
                if (firstLine && line.toLowerCase(Locale.ROOT).startsWith("email")) {
                    firstLine = false;
                    continue;
                }
                firstLine = false;
                String email = line.split(",")[0].trim().replace("\"", "");
                if (!email.isBlank()) emails.add(email);
            }
        }
        return emails;
    }

    private CampaignResponse toResponse(Campaign campaign) {
        return new CampaignResponse(
                campaign.id(),
                campaign.tenantId(),
                campaign.domainId(),
                campaign.name(),
                campaign.subject(),
                campaign.recipientCount(),
                store.listMessageJobs(campaign.id()).size(),
                campaign.status().name(),
                campaign.createdAt()
        );
    }

    private MessageJobResponse toResponse(MessageJob messageJob) {
        return new MessageJobResponse(
                messageJob.id(),
                messageJob.campaignId(),
                messageJob.tenantId(),
                messageJob.domainId(),
                messageJob.recipientId(),
                messageJob.recipientEmail(),
                messageJob.status().name(),
                messageJob.scheduledAt(),
                messageJob.claimedAt(),
                messageJob.completedAt(),
                messageJob.lastError(),
                messageJob.createdAt()
        );
    }

    private OutboundMessageResponse toResponse(OutboundMessage message) {
        return new OutboundMessageResponse(
                message.id(),
                message.messageJobId(),
                message.campaignId(),
                message.tenantId(),
                message.domainId(),
                message.ipPoolId(),
                message.outboundIpId(),
                message.outboundIpAddress(),
                message.recipientEmail(),
                message.subject(),
                message.deliveryStatus(),
                message.sentAt()
        );
    }

    private List<String> normalizeRecipients(CreateCampaignRequest request) {
        LinkedHashSet<String> recipients = new LinkedHashSet<>();
        if (request.recipientEmail() != null && !request.recipientEmail().isBlank()) {
            recipients.add(normalizeEmail(request.recipientEmail()));
        }
        if (request.recipientEmails() != null) {
            request.recipientEmails().stream()
                    .filter(email -> email != null && !email.isBlank())
                    .map(this::normalizeEmail)
                    .forEach(recipients::add);
        }
        if (recipients.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one recipient is required");
        }
        return List.copyOf(recipients);
    }

    private String normalizeEmail(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid recipient email: " + email);
        }
        return normalized;
    }
}
