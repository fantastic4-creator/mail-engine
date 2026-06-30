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
import java.time.Instant;
import java.util.List;
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
            if (outboundMessage.deliveryStatus().startsWith("SMTP_FAILED")) {
                if (messageJob.retryCount() < runtimeProperties.getRetryMaxAttempts()) {
                    long backoffSeconds = runtimeProperties.getRetryBackoffSeconds() * (1L << messageJob.retryCount());
                    Instant nextRetryAt = Instant.now().plusSeconds(backoffSeconds);
                    store.saveMessageJob(messageJob.scheduleRetry(nextRetryAt));
                } else {
                    store.saveMessageJob(messageJob.complete(
                            MessageJobStatus.FAILED,
                            outboundMessage.sentAt(),
                            outboundMessage.deliveryStatus()));
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
}
