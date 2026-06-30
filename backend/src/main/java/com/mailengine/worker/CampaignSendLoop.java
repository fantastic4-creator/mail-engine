package com.mailengine.worker;

import com.mailengine.domain.Campaign;
import com.mailengine.domain.OutboundIp;

public interface CampaignSendLoop {

    void process(Campaign campaign, OutboundIp outboundIp);
}
