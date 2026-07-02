package com.mailengine.worker;

import com.mailengine.config.MailEngineRuntimeProperties;
import com.mailengine.data.PlatformStateStore;
import com.mailengine.delivery.DeliveryGateway;
import com.mailengine.domain.Campaign;
import com.mailengine.domain.CampaignStatus;
import com.mailengine.domain.MessageJob;
import com.mailengine.domain.MessageJobStatus;
import com.mailengine.domain.OutboundIp;
import com.mailengine.domain.OutboundMessage;
import com.mailengine.domain.SuppressionRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultCampaignSendLoop implements CampaignSendLoop {

    private static final int LOCAL_CLAIM_LIMIT = 1_000;

    private final PlatformStateStore store;
    private final DeliveryGateway deliveryGateway;
    private final MailEngineRuntimeProperties runtimeProperties;

    public DefaultCampaignSendLoop(
            PlatformStateStore store,
            DeliveryGateway deliveryGateway,
            MailEngineRuntimeProperties runtimeProperties) {
        this.store = store;
        this.deliveryGateway = deliveryGateway;
        this.runtimeProperties = runtimeProperties;
    }

    @Override
    public void process(Campaign campaign, OutboundIp outboundIp) {
        int maxPerHour = campaign.maxSendsPerHour() > 0
                ? campaign.maxSendsPerHour()
                : runtimeProperties.getMaxSendsPerHour();

        List<MessageJob> claimedJobs;
        if (maxPerHour > 0) {
            int sentLastHour = store.countSentJobsSince(campaign.tenantId(), Instant.now().minusSeconds(3600));
            int remaining = maxPerHour - sentLastHour;
            if (remaining <= 0) return;
            claimedJobs = store.claimPendingMessageJobs(campaign.id(), Math.min(LOCAL_CLAIM_LIMIT, remaining));
        } else {
            claimedJobs = store.claimPendingMessageJobs(campaign.id(), LOCAL_CLAIM_LIMIT);
        }

        for (MessageJob messageJob : claimedJobs) {
            if (store.isSuppressed(messageJob.tenantId(), messageJob.recipientEmail())) {
                store.saveMessageJob(messageJob.complete(MessageJobStatus.SUPPRESSED, Instant.now(), "Recipient is suppressed"));
                continue;
            }

            OutboundMessage outboundMessage = deliveryGateway.deliver(campaign, messageJob, outboundIp);
            String status = outboundMessage.deliveryStatus();
            if (status.startsWith("SMTP_HARD_BOUNCE")) {
                suppress(messageJob, "BOUNCE");
                store.saveMessageJob(messageJob.complete(MessageJobStatus.FAILED, outboundMessage.sentAt(), status));
            } else if (status.startsWith("SMTP_FAILED")) {
                if (messageJob.retryCount() < runtimeProperties.getRetryMaxAttempts()) {
                    long backoffSeconds = runtimeProperties.getRetryBackoffSeconds() * (1L << messageJob.retryCount());
                    store.saveMessageJob(messageJob.scheduleRetry(Instant.now().plusSeconds(backoffSeconds)));
                } else {
                    suppress(messageJob, "BOUNCE");
                    store.saveMessageJob(messageJob.complete(MessageJobStatus.FAILED, outboundMessage.sentAt(), status));
                }
            } else {
                store.saveMessageJob(messageJob.complete(MessageJobStatus.SENT, outboundMessage.sentAt(), null));
            }
        }

        boolean hasPending = store.listMessageJobs(campaign.id()).stream()
                .anyMatch(j -> j.status() == MessageJobStatus.PENDING || j.status() == MessageJobStatus.CLAIMED);
        if (!hasPending) {
            store.saveCampaign(campaign.withStatus(CampaignStatus.SENT));
        }
    }

    private void suppress(MessageJob messageJob, String reason) {
        store.saveSuppression(new SuppressionRecord(
                UUID.randomUUID(),
                messageJob.tenantId(),
                messageJob.recipientEmail(),
                reason,
                Instant.now()));
    }
}
