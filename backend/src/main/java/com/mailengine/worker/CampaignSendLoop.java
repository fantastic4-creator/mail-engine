package com.mailengine.worker;

import com.mailengine.domain.Campaign;
import com.mailengine.domain.OutboundIp;
import com.mailengine.domain.OutboundMessage;
import java.util.List;

public interface CampaignSendLoop {

    List<OutboundMessage> process(Campaign campaign, OutboundIp outboundIp);
}
