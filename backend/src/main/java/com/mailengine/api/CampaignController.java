package com.mailengine.api;

import com.mailengine.api.dto.CampaignResponse;
import com.mailengine.api.dto.CreateCampaignRequest;
import com.mailengine.api.dto.MessageJobResponse;
import com.mailengine.api.dto.OutboundMessageResponse;
import com.mailengine.service.CampaignService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CampaignResponse createCampaign(@Valid @RequestBody CreateCampaignRequest request) {
        return campaignService.createCampaign(request);
    }

    @GetMapping
    public List<CampaignResponse> listCampaigns() {
        return campaignService.listCampaigns();
    }

    @GetMapping("/{campaignId}")
    public CampaignResponse getCampaign(@PathVariable UUID campaignId) {
        return campaignService.getCampaign(campaignId);
    }

    @GetMapping("/{campaignId}/jobs")
    public List<MessageJobResponse> listCampaignJobs(@PathVariable UUID campaignId) {
        return campaignService.listCampaignJobs(campaignId);
    }

    @GetMapping("/outbox")
    public List<OutboundMessageResponse> listOutbox() {
        return campaignService.listOutbox();
    }
}
