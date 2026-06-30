package com.mailengine.worker;

import com.mailengine.data.PlatformStateStore;
import com.mailengine.domain.Campaign;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SendLoopScheduler {

    private final PlatformStateStore store;
    private final CampaignSendLoop sendLoop;

    public SendLoopScheduler(PlatformStateStore store, CampaignSendLoop sendLoop) {
        this.store = store;
        this.sendLoop = sendLoop;
    }

    @Scheduled(fixedDelayString = "${mail-engine.runtime.send-loop-poll-ms:5000}")
    public void poll() {
        for (Campaign campaign : store.listCampaignsWithPendingJobs()) {
            store.findFirstActiveOutboundIp(campaign.tenantId())
                    .ifPresent(ip -> sendLoop.process(campaign, ip));
        }
    }
}
