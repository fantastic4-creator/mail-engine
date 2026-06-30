package com.mailengine.data;

import com.mailengine.domain.ApiKey;
import com.mailengine.domain.Campaign;
import com.mailengine.domain.IpPool;
import com.mailengine.domain.MessageJob;
import com.mailengine.domain.OutboundIp;
import com.mailengine.domain.OutboundMessage;
import com.mailengine.domain.Recipient;
import com.mailengine.domain.SendingDomain;
import com.mailengine.domain.SuppressionRecord;
import com.mailengine.domain.Tenant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformStateStore {

    Tenant saveTenant(Tenant tenant);

    Optional<Tenant> findTenant(UUID tenantId);

    List<Tenant> listTenants();

    SendingDomain saveDomain(SendingDomain domain);

    Optional<SendingDomain> findDomain(UUID domainId);

    Optional<SendingDomain> findDomainForTenant(UUID tenantId, UUID domainId);

    List<SendingDomain> listDomains(UUID tenantId);

    IpPool saveIpPool(IpPool ipPool);

    Optional<IpPool> findIpPoolForTenant(UUID tenantId, UUID ipPoolId);

    List<IpPool> listIpPools(UUID tenantId);

    OutboundIp saveOutboundIp(OutboundIp outboundIp);

    List<OutboundIp> listOutboundIps(UUID tenantId, UUID ipPoolId);

    Optional<OutboundIp> findFirstActiveOutboundIp(UUID tenantId);

    Campaign saveCampaign(Campaign campaign);

    Optional<Campaign> findCampaign(UUID campaignId);

    List<Campaign> listCampaigns();

    List<Campaign> listCampaignsWithPendingJobs();

    Recipient saveRecipient(Recipient recipient);

    List<Recipient> listRecipients(UUID campaignId);

    MessageJob saveMessageJob(MessageJob messageJob);

    List<MessageJob> claimPendingMessageJobs(UUID campaignId, int limit);

    List<MessageJob> claimDueRetryJobs(int limit);

    List<MessageJob> listMessageJobs(UUID campaignId);

    SuppressionRecord saveSuppression(SuppressionRecord suppressionRecord);

    List<SuppressionRecord> listSuppressions(UUID tenantId);

    boolean isSuppressed(UUID tenantId, String email);

    OutboundMessage saveOutboundMessage(OutboundMessage message);

    List<OutboundMessage> listOutboundMessages();

    ApiKey saveApiKey(ApiKey apiKey);

    Optional<ApiKey> findApiKeyByHash(String keyHash);

    List<ApiKey> listApiKeys(UUID tenantId);

    void deleteApiKey(UUID keyId);

    void touchApiKeyLastUsed(UUID keyId);

    int cancelCampaignJobs(UUID campaignId);
}
