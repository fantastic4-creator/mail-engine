package com.mailengine.data;

import com.mailengine.data.entity.ApiKeyEntity;
import com.mailengine.data.entity.CampaignEntity;
import com.mailengine.data.entity.IpPoolEntity;
import com.mailengine.data.entity.MessageJobEntity;
import com.mailengine.data.entity.OutboundIpEntity;
import com.mailengine.data.entity.OutboundMessageEntity;
import com.mailengine.data.entity.RecipientEntity;
import com.mailengine.data.entity.SendingDomainEntity;
import com.mailengine.data.entity.SuppressionRecordEntity;
import com.mailengine.data.entity.TenantEntity;
import com.mailengine.data.repository.ApiKeyRepository;
import com.mailengine.data.repository.CampaignRepository;
import com.mailengine.data.repository.IpPoolRepository;
import com.mailengine.data.repository.MessageJobRepository;
import com.mailengine.data.repository.OutboundIpRepository;
import com.mailengine.data.repository.OutboundMessageRepository;
import com.mailengine.data.repository.RecipientRepository;
import com.mailengine.data.repository.SendingDomainRepository;
import com.mailengine.data.repository.SuppressionRecordRepository;
import com.mailengine.data.repository.TenantRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "mail-engine.runtime.storage-mode", havingValue = "postgres")
@Transactional
public class JpaPlatformStateStore implements PlatformStateStore {

    private final TenantRepository tenants;
    private final SendingDomainRepository domains;
    private final IpPoolRepository ipPools;
    private final OutboundIpRepository outboundIps;
    private final CampaignRepository campaigns;
    private final RecipientRepository recipients;
    private final MessageJobRepository messageJobs;
    private final SuppressionRecordRepository suppressions;
    private final OutboundMessageRepository outboundMessages;
    private final ApiKeyRepository apiKeys;

    public JpaPlatformStateStore(
            TenantRepository tenants,
            SendingDomainRepository domains,
            IpPoolRepository ipPools,
            OutboundIpRepository outboundIps,
            CampaignRepository campaigns,
            RecipientRepository recipients,
            MessageJobRepository messageJobs,
            SuppressionRecordRepository suppressions,
            OutboundMessageRepository outboundMessages,
            ApiKeyRepository apiKeys) {
        this.tenants = tenants;
        this.domains = domains;
        this.ipPools = ipPools;
        this.outboundIps = outboundIps;
        this.campaigns = campaigns;
        this.recipients = recipients;
        this.messageJobs = messageJobs;
        this.suppressions = suppressions;
        this.outboundMessages = outboundMessages;
        this.apiKeys = apiKeys;
    }

    @Override
    public Tenant saveTenant(Tenant tenant) {
        return tenants.save(TenantEntity.from(tenant)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tenant> findTenant(UUID tenantId) {
        return tenants.findById(tenantId).map(TenantEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Tenant> listTenants() {
        return tenants.findAllByOrderByCreatedAtDesc().stream()
                .map(TenantEntity::toDomain)
                .toList();
    }

    @Override
    public SendingDomain saveDomain(SendingDomain domain) {
        return domains.save(SendingDomainEntity.from(domain)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SendingDomain> findDomain(UUID domainId) {
        return domains.findById(domainId).map(SendingDomainEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SendingDomain> findDomainForTenant(UUID tenantId, UUID domainId) {
        return domains.findByIdAndTenantId(domainId, tenantId).map(SendingDomainEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SendingDomain> listDomains(UUID tenantId) {
        return domains.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(SendingDomainEntity::toDomain)
                .toList();
    }

    @Override
    public IpPool saveIpPool(IpPool ipPool) {
        return ipPools.save(IpPoolEntity.from(ipPool)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IpPool> findIpPoolForTenant(UUID tenantId, UUID ipPoolId) {
        return ipPools.findByIdAndTenantId(ipPoolId, tenantId).map(IpPoolEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IpPool> listIpPools(UUID tenantId) {
        return ipPools.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(IpPoolEntity::toDomain)
                .toList();
    }

    @Override
    public OutboundIp saveOutboundIp(OutboundIp outboundIp) {
        return outboundIps.save(OutboundIpEntity.from(outboundIp)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboundIp> listOutboundIps(UUID tenantId, UUID ipPoolId) {
        return outboundIps.findByTenantIdAndIpPoolIdOrderByCreatedAtDesc(tenantId, ipPoolId).stream()
                .map(OutboundIpEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OutboundIp> findFirstActiveOutboundIp(UUID tenantId) {
        return outboundIps.findFirstByTenantIdAndStatusOrderByCreatedAt(tenantId, "ACTIVE")
                .map(OutboundIpEntity::toDomain);
    }

    @Override
    public Campaign saveCampaign(Campaign campaign) {
        return campaigns.save(CampaignEntity.from(campaign)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Campaign> findCampaign(UUID campaignId) {
        return campaigns.findById(campaignId).map(CampaignEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Campaign> listCampaigns() {
        return campaigns.findAllByOrderByCreatedAtDesc().stream()
                .map(CampaignEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Campaign> listCampaignsWithPendingJobs() {
        return campaigns.findCampaignsWithJobStatus(MessageJobStatus.PENDING).stream()
                .map(CampaignEntity::toDomain)
                .toList();
    }

    @Override
    public Recipient saveRecipient(Recipient recipient) {
        return recipients.save(RecipientEntity.from(recipient)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Recipient> listRecipients(UUID campaignId) {
        return recipients.findByCampaignIdOrderByCreatedAt(campaignId).stream()
                .map(RecipientEntity::toDomain)
                .toList();
    }

    @Override
    public MessageJob saveMessageJob(MessageJob messageJob) {
        return messageJobs.save(MessageJobEntity.from(messageJob)).toDomain();
    }

    @Override
    public List<MessageJob> claimPendingMessageJobs(UUID campaignId, int limit) {
        Instant now = Instant.now();
        List<MessageJobEntity> pending = messageJobs.lockPendingJobs(
                campaignId, now, MessageJobStatus.PENDING, PageRequest.of(0, limit));
        if (pending.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = pending.stream().map(MessageJobEntity::getId).toList();
        messageJobs.markClaimed(ids, MessageJobStatus.CLAIMED, now);
        return messageJobs.findAllById(ids).stream()
                .map(MessageJobEntity::toDomain)
                .toList();
    }

    @Override
    public List<MessageJob> claimDueRetryJobs(int limit) {
        Instant now = Instant.now();
        List<MessageJobEntity> due = messageJobs.lockRetryJobs(
                MessageJobStatus.RETRY_SCHEDULED, now, PageRequest.of(0, limit));
        if (due.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = due.stream().map(MessageJobEntity::getId).toList();
        messageJobs.resetRetryToPending(ids, MessageJobStatus.PENDING);
        return messageJobs.findAllById(ids).stream()
                .map(MessageJobEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageJob> listMessageJobs(UUID campaignId) {
        return messageJobs.findByCampaignIdOrderByCreatedAt(campaignId).stream()
                .map(MessageJobEntity::toDomain)
                .toList();
    }

    @Override
    public SuppressionRecord saveSuppression(SuppressionRecord suppressionRecord) {
        return suppressions.save(SuppressionRecordEntity.from(suppressionRecord)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SuppressionRecord> listSuppressions(UUID tenantId) {
        return suppressions.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(SuppressionRecordEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSuppressed(UUID tenantId, String email) {
        return suppressions.existsByTenantIdAndEmailIgnoreCase(tenantId, email);
    }

    @Override
    public OutboundMessage saveOutboundMessage(OutboundMessage message) {
        return outboundMessages.save(OutboundMessageEntity.from(message)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboundMessage> listOutboundMessages() {
        return outboundMessages.findAllByOrderBySentAtDesc().stream()
                .map(OutboundMessageEntity::toDomain)
                .toList();
    }

    @Override
    public ApiKey saveApiKey(ApiKey apiKey) {
        return apiKeys.save(ApiKeyEntity.from(apiKey)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ApiKey> findApiKeyByHash(String keyHash) {
        return apiKeys.findByKeyHash(keyHash).map(ApiKeyEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiKey> listApiKeys(UUID tenantId) {
        return apiKeys.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(ApiKeyEntity::toDomain)
                .toList();
    }

    @Override
    public void deleteApiKey(UUID keyId) {
        apiKeys.deleteById(keyId);
    }

    @Override
    public void touchApiKeyLastUsed(UUID keyId) {
        apiKeys.touchLastUsed(keyId, Instant.now());
    }

    @Override
    public int cancelCampaignJobs(UUID campaignId) {
        return messageJobs.cancelActiveJobs(
                campaignId,
                List.of(MessageJobStatus.PENDING, MessageJobStatus.CLAIMED),
                MessageJobStatus.FAILED,
                Instant.now(),
                "Campaign cancelled");
    }
}
