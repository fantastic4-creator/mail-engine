package com.mailengine.worker;

import com.mailengine.data.PlatformStateStore;
import com.mailengine.domain.MessageJob;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RetryScheduler {

    private final PlatformStateStore store;
    private final CampaignSendLoop sendLoop;

    public RetryScheduler(PlatformStateStore store, CampaignSendLoop sendLoop) {
        this.store = store;
        this.sendLoop = sendLoop;
    }

    @Scheduled(fixedDelay = 60_000)
    public void retryDueJobs() {
        List<MessageJob> due = store.claimDueRetryJobs(500);
        due.stream()
                .map(MessageJob::campaignId)
                .distinct()
                .collect(Collectors.toList())
                .forEach(campaignId -> store.findCampaign(campaignId).ifPresent(campaign ->
                        store.findFirstActiveOutboundIp(campaign.tenantId())
                                .ifPresent(ip -> sendLoop.process(campaign, ip))));
    }
}
