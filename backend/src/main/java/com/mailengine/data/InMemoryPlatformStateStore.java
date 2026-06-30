package com.mailengine.data;

import com.mailengine.domain.ApiKey;
import com.mailengine.domain.Campaign;
import com.mailengine.domain.IpPool;
import com.mailengine.domain.MessageJob;
import com.mailengine.domain.MessageJobStatus;
import com.mailengine.domain.OutboundIp;
import com.mailengine.domain.OutboundMessage;
import com.mailengine.domain.Recipient;
import com.mailengine.domain.SendingDomain;
import com.mailengine.domain.SuppressionRecord;
import com.mailengine.domain.Tenant;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "mail-engine.runtime.storage-mode", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryPlatformStateStore implements PlatformStateStore {

    private final ConcurrentMap<UUID, ApiKey> apiKeys = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Tenant> tenants = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, SendingDomain> domains = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Campaign> campaigns = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Recipient> recipients = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, MessageJob> messageJobs = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, OutboundMessage> outboundMessages = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, IpPool> ipPools = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, OutboundIp> outboundIps = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, SuppressionRecord> suppressions = new ConcurrentHashMap<>();

    @Override
    public Tenant saveTenant(Tenant tenant) {
        tenants.put(tenant.id(), tenant);
        return tenant;
    }

    @Override
    public Optional<Tenant> findTenant(UUID tenantId) {
        return Optional.ofNullable(tenants.get(tenantId));
    }

    @Override
    public List<Tenant> listTenants() {
        return tenants.values().stream()
                .sorted(Comparator.comparing(Tenant::createdAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public SendingDomain saveDomain(SendingDomain domain) {
        domains.put(domain.id(), domain);
        return domain;
    }

    @Override
    public IpPool saveIpPool(IpPool ipPool) {
        ipPools.put(ipPool.id(), ipPool);
        return ipPool;
    }

    @Override
    public Optional<IpPool> findIpPoolForTenant(UUID tenantId, UUID ipPoolId) {
        return Optional.ofNullable(ipPools.get(ipPoolId))
                .filter(ipPool -> ipPool.tenantId().equals(tenantId));
    }

    @Override
    public List<IpPool> listIpPools(UUID tenantId) {
        return ipPools.values().stream()
                .filter(ipPool -> ipPool.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(IpPool::createdAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public OutboundIp saveOutboundIp(OutboundIp outboundIp) {
        outboundIps.put(outboundIp.id(), outboundIp);
        return outboundIp;
    }

    @Override
    public List<OutboundIp> listOutboundIps(UUID tenantId, UUID ipPoolId) {
        return outboundIps.values().stream()
                .filter(outboundIp -> outboundIp.tenantId().equals(tenantId))
                .filter(outboundIp -> outboundIp.ipPoolId().equals(ipPoolId))
                .sorted(Comparator.comparing(OutboundIp::createdAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public Optional<OutboundIp> findFirstActiveOutboundIp(UUID tenantId) {
        return outboundIps.values().stream()
                .filter(outboundIp -> outboundIp.tenantId().equals(tenantId))
                .filter(outboundIp -> "ACTIVE".equals(outboundIp.status()))
                .sorted(Comparator.comparing(OutboundIp::createdAt))
                .findFirst();
    }

    @Override
    public Optional<SendingDomain> findDomain(UUID domainId) {
        return Optional.ofNullable(domains.get(domainId));
    }

    @Override
    public List<SendingDomain> listDomains(UUID tenantId) {
        return domains.values().stream()
                .filter(domain -> domain.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(SendingDomain::createdAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public Optional<SendingDomain> findDomainForTenant(UUID tenantId, UUID domainId) {
        return Optional.ofNullable(domains.get(domainId))
                .filter(domain -> domain.tenantId().equals(tenantId));
    }

    @Override
    public Campaign saveCampaign(Campaign campaign) {
        campaigns.put(campaign.id(), campaign);
        return campaign;
    }

    @Override
    public Optional<Campaign> findCampaign(UUID campaignId) {
        return Optional.ofNullable(campaigns.get(campaignId));
    }

    @Override
    public List<Campaign> listCampaigns() {
        return campaigns.values().stream()
                .sorted(Comparator.comparing(Campaign::createdAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<Campaign> listCampaignsWithPendingJobs() {
        return messageJobs.values().stream()
                .filter(j -> j.status() == MessageJobStatus.PENDING)
                .map(MessageJob::campaignId)
                .distinct()
                .map(campaigns::get)
                .filter(c -> c != null)
                .collect(Collectors.toList());
    }

    @Override
    public Recipient saveRecipient(Recipient recipient) {
        recipients.put(recipient.id(), recipient);
        return recipient;
    }

    @Override
    public List<Recipient> listRecipients(UUID campaignId) {
        return recipients.values().stream()
                .filter(recipient -> recipient.campaignId().equals(campaignId))
                .sorted(Comparator.comparing(Recipient::createdAt))
                .collect(Collectors.toList());
    }

    @Override
    public MessageJob saveMessageJob(MessageJob messageJob) {
        messageJobs.put(messageJob.id(), messageJob);
        return messageJob;
    }

    @Override
    public List<MessageJob> claimPendingMessageJobs(UUID campaignId, int limit) {
        Instant now = Instant.now();
        return messageJobs.values().stream()
                .filter(messageJob -> messageJob.campaignId().equals(campaignId))
                .filter(messageJob -> messageJob.status() == MessageJobStatus.PENDING)
                .filter(messageJob -> !messageJob.scheduledAt().isAfter(now))
                .sorted(Comparator.comparing(MessageJob::createdAt))
                .limit(limit)
                .map(messageJob -> saveMessageJob(messageJob.claim(now)))
                .collect(Collectors.toList());
    }

    @Override
    public List<MessageJob> claimDueRetryJobs(int limit) {
        Instant now = Instant.now();
        List<MessageJob> dueJobs = messageJobs.values().stream()
                .filter(j -> j.status() == MessageJobStatus.RETRY_SCHEDULED)
                .filter(j -> j.nextRetryAt() != null && !j.nextRetryAt().isAfter(now))
                .sorted(Comparator.comparing(MessageJob::nextRetryAt))
                .limit(limit)
                .collect(Collectors.toList());
        return dueJobs.stream()
                .map(j -> saveMessageJob(new MessageJob(
                        j.id(), j.campaignId(), j.tenantId(), j.domainId(),
                        j.recipientId(), j.recipientEmail(), MessageJobStatus.PENDING,
                        j.scheduledAt(), null, null, null, j.retryCount(), null, j.createdAt())))
                .collect(Collectors.toList());
    }

    @Override
    public List<MessageJob> listMessageJobs(UUID campaignId) {
        return messageJobs.values().stream()
                .filter(messageJob -> messageJob.campaignId().equals(campaignId))
                .sorted(Comparator.comparing(MessageJob::createdAt))
                .collect(Collectors.toList());
    }

    @Override
    public SuppressionRecord saveSuppression(SuppressionRecord suppressionRecord) {
        suppressions.put(suppressionRecord.id(), suppressionRecord);
        return suppressionRecord;
    }

    @Override
    public List<SuppressionRecord> listSuppressions(UUID tenantId) {
        return suppressions.values().stream()
                .filter(suppression -> suppression.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(SuppressionRecord::createdAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public boolean isSuppressed(UUID tenantId, String email) {
        return suppressions.values().stream()
                .filter(suppression -> suppression.tenantId().equals(tenantId))
                .anyMatch(suppression -> suppression.email().equalsIgnoreCase(email));
    }

    @Override
    public OutboundMessage saveOutboundMessage(OutboundMessage message) {
        outboundMessages.put(message.id(), message);
        return message;
    }

    @Override
    public List<OutboundMessage> listOutboundMessages() {
        return outboundMessages.values().stream()
                .sorted(Comparator.comparing(OutboundMessage::sentAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public ApiKey saveApiKey(ApiKey apiKey) {
        apiKeys.put(apiKey.id(), apiKey);
        return apiKey;
    }

    @Override
    public Optional<ApiKey> findApiKeyByHash(String keyHash) {
        return apiKeys.values().stream()
                .filter(k -> k.keyHash().equals(keyHash))
                .findFirst();
    }

    @Override
    public List<ApiKey> listApiKeys(UUID tenantId) {
        return apiKeys.values().stream()
                .filter(k -> k.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(ApiKey::createdAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public void deleteApiKey(UUID keyId) {
        apiKeys.remove(keyId);
    }

    @Override
    public void touchApiKeyLastUsed(UUID keyId) {
        ApiKey key = apiKeys.get(keyId);
        if (key != null) {
            apiKeys.put(keyId, new ApiKey(key.id(), key.tenantId(), key.name(), key.keyHash(), key.keyPrefix(), Instant.now(), key.createdAt()));
        }
    }

    @Override
    public int cancelCampaignJobs(UUID campaignId) {
        Instant now = Instant.now();
        List<MessageJob> toCancel = messageJobs.values().stream()
                .filter(j -> j.campaignId().equals(campaignId))
                .filter(j -> j.status() == MessageJobStatus.PENDING || j.status() == MessageJobStatus.CLAIMED)
                .collect(Collectors.toList());
        toCancel.forEach(j -> messageJobs.put(j.id(), j.complete(MessageJobStatus.FAILED, now, "Campaign cancelled")));
        return toCancel.size();
    }
}
