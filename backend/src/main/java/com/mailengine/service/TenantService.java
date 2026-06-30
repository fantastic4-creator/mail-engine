package com.mailengine.service;

import com.mailengine.api.dto.CreateDomainRequest;
import com.mailengine.api.dto.CreateTenantRequest;
import com.mailengine.api.dto.DnsRecordResponse;
import com.mailengine.api.dto.DomainResponse;
import com.mailengine.api.dto.TenantResponse;
import com.mailengine.config.MailEngineRuntimeProperties;
import com.mailengine.data.PlatformStateStore;
import com.mailengine.domain.DomainVerificationStatus;
import com.mailengine.domain.SendingDomain;
import com.mailengine.domain.Tenant;
import java.time.Instant;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantService {

    private final PlatformStateStore store;
    private final MailEngineRuntimeProperties runtimeProperties;
    private final DkimKeyGenerator dkimKeyGenerator;

    public TenantService(
            PlatformStateStore store,
            MailEngineRuntimeProperties runtimeProperties,
            DkimKeyGenerator dkimKeyGenerator
    ) {
        this.store = store;
        this.runtimeProperties = runtimeProperties;
        this.dkimKeyGenerator = dkimKeyGenerator;
    }

    public TenantResponse createTenant(CreateTenantRequest request) {
        Tenant tenant = new Tenant(UUID.randomUUID(), request.name().trim(), Instant.now());
        store.saveTenant(tenant);
        return toTenantResponse(tenant);
    }

    public List<TenantResponse> listTenants() {
        return store.listTenants().stream()
                .map(this::toTenantResponse)
                .toList();
    }

    public TenantResponse getTenant(UUID tenantId) {
        Tenant tenant = requireTenant(tenantId);
        return toTenantResponse(tenant);
    }

    public DomainResponse addDomain(UUID tenantId, CreateDomainRequest request) {
        Tenant tenant = requireTenant(tenantId);
        String normalizedDomain = normalizeDomain(request.domainName());
        ensureDomainNotAlreadyUsed(tenant.id(), normalizedDomain);
        DkimKeyMaterial dkimKeyMaterial = dkimKeyGenerator.generate(runtimeProperties.getDkimSelector());

        SendingDomain domain = new SendingDomain(
                UUID.randomUUID(),
                tenant.id(),
                normalizedDomain,
                DomainVerificationStatus.PENDING,
                UUID.randomUUID().toString(),
                dkimKeyMaterial.selector(),
                dkimKeyMaterial.publicKey(),
                dkimKeyMaterial.privateKeyPem(),
                Instant.now(),
                null
        );
        store.saveDomain(domain);
        return toDomainResponse(domain);
    }

    public List<DomainResponse> listDomains(UUID tenantId) {
        requireTenant(tenantId);
        return store.listDomains(tenantId).stream()
                .map(this::toDomainResponse)
                .toList();
    }

    public DomainResponse verifyDomain(UUID tenantId, UUID domainId) {
        requireTenant(tenantId);
        SendingDomain domain = requireDomain(domainId);
        if (!domain.tenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Domain not found for tenant");
        }

        if (runtimeProperties.isSkipDomainVerification()) {
            SendingDomain verified = domain.withVerificationStatus(DomainVerificationStatus.VERIFIED, Instant.now());
            store.saveDomain(verified);
            return toDomainResponse(verified);
        }

        String expectedRecord = "mail-engine-verification=" + domain.verificationToken();
        String lookupName = "_mailengine." + domain.domainName();

        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(lookupName, new String[]{"TXT"});
            Attribute txtAttr = attrs.get("TXT");
            if (txtAttr == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "DNS TXT record not found or does not match verification token");
            }
            boolean found = false;
            for (int i = 0; i < txtAttr.size(); i++) {
                String val = txtAttr.get(i).toString();
                if (val.contains(expectedRecord)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "DNS TXT record not found or does not match verification token");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (NamingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "DNS TXT record not found or does not match verification token");
        }

        SendingDomain verifiedDomain = domain.withVerificationStatus(DomainVerificationStatus.VERIFIED, Instant.now());
        store.saveDomain(verifiedDomain);
        return toDomainResponse(verifiedDomain);
    }

    public List<DnsRecordResponse> listDomainDnsRecords(UUID tenantId, UUID domainId) {
        requireTenant(tenantId);
        SendingDomain domain = requireDomain(domainId);
        if (!domain.tenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Domain not found for tenant");
        }

        return List.of(
                new DnsRecordResponse(
                        "TXT",
                        "_mailengine." + domain.domainName(),
                        "mail-engine-verification=" + domain.verificationToken(),
                        "Domain ownership verification"
                ),
                new DnsRecordResponse(
                        "TXT",
                        domain.dkimSelector() + "._domainkey." + domain.domainName(),
                        "v=DKIM1; k=rsa; p=" + domain.dkimPublicKey(),
                        "DKIM public key for mail signing"
                ),
                new DnsRecordResponse(
                        "TXT",
                        domain.domainName(),
                        runtimeProperties.getSpfRecordValue(),
                        "SPF sender authorization starter record"
                ),
                new DnsRecordResponse(
                        "TXT",
                        "_dmarc." + domain.domainName(),
                        "v=DMARC1; p=" + runtimeProperties.getDmarcPolicy()
                                + "; rua=mailto:postmaster@" + domain.domainName(),
                        "Starter DMARC policy"
                )
        );
    }

    private Tenant requireTenant(UUID tenantId) {
        Tenant tenant = store.findTenant(tenantId).orElse(null);
        if (tenant == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found");
        }
        return tenant;
    }

    private SendingDomain requireDomain(UUID domainId) {
        SendingDomain domain = store.findDomain(domainId).orElse(null);
        if (domain == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Domain not found");
        }
        return domain;
    }

    private void ensureDomainNotAlreadyUsed(UUID tenantId, String normalizedDomain) {
        boolean duplicate = store.listDomains(tenantId).stream()
                .anyMatch(domain -> domain.domainName().equalsIgnoreCase(normalizedDomain));
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Domain already exists for tenant");
        }
    }

    private String normalizeDomain(String domain) {
        String normalized = domain.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "domainName is required");
        }
        return normalized;
    }

    private TenantResponse toTenantResponse(Tenant tenant) {
        long domainCount = store.listDomains(tenant.id()).size();
        return new TenantResponse(tenant.id(), tenant.name(), tenant.createdAt(), domainCount);
    }

    private DomainResponse toDomainResponse(SendingDomain domain) {
        return new DomainResponse(
                domain.id(),
                domain.tenantId(),
                domain.domainName(),
                domain.verificationStatus().name(),
                domain.verificationToken(),
                domain.dkimSelector(),
                domain.createdAt(),
                domain.verifiedAt()
        );
    }
}
