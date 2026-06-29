package com.mailengine.delivery;

import com.mailengine.domain.Campaign;
import com.mailengine.domain.MessageJob;
import com.mailengine.domain.OutboundIp;
import com.mailengine.domain.OutboundMessage;

public interface DeliveryGateway {

    OutboundMessage deliver(Campaign campaign, MessageJob messageJob, OutboundIp outboundIp);
}
