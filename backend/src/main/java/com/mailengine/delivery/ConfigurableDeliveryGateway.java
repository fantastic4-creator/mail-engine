package com.mailengine.delivery;

import com.mailengine.config.MailEngineRuntimeProperties;
import com.mailengine.data.PlatformStateStore;
import com.mailengine.domain.Campaign;
import com.mailengine.domain.MessageJob;
import com.mailengine.domain.OutboundIp;
import com.mailengine.domain.OutboundMessage;
import com.mailengine.domain.SendingDomain;
import com.mailengine.service.UnsubscribeTokenService;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.MimeMessage;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;
import net.markenwerk.utils.mail.dkim.DkimException;
import net.markenwerk.utils.mail.dkim.DkimMessage;
import net.markenwerk.utils.mail.dkim.DkimSigner;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class ConfigurableDeliveryGateway implements DeliveryGateway {

    private final MailEngineRuntimeProperties runtimeProperties;
    private final PlatformStateStore store;
    private final UnsubscribeTokenService unsubscribeTokenService;

    public ConfigurableDeliveryGateway(
            MailEngineRuntimeProperties runtimeProperties,
            PlatformStateStore store,
            UnsubscribeTokenService unsubscribeTokenService) {
        this.runtimeProperties = runtimeProperties;
        this.store = store;
        this.unsubscribeTokenService = unsubscribeTokenService;
    }

    @Override
    public OutboundMessage deliver(Campaign campaign, MessageJob messageJob, OutboundIp outboundIp) {
        String status = switch (runtimeProperties.getDeliveryMode()) {
            case LOCAL_OUTBOX -> "LOCAL_CAPTURED";
            case SMTP -> sendSmtp(campaign, messageJob);
        };

        OutboundMessage message = new OutboundMessage(
                UUID.randomUUID(),
                messageJob.id(),
                campaign.id(),
                campaign.tenantId(),
                campaign.domainId(),
                outboundIp.ipPoolId(),
                outboundIp.id(),
                outboundIp.publicIpAddress(),
                messageJob.recipientEmail(),
                campaign.subject(),
                status,
                Instant.now()
        );
        return store.saveOutboundMessage(message);
    }

    private String sendSmtp(Campaign campaign, MessageJob messageJob) {
        SendingDomain domain = store.findDomain(campaign.domainId())
                .orElseThrow(() -> new IllegalStateException("Campaign domain not found"));

        JavaMailSenderImpl mailSender = buildMailSender();

        try {
            String from = runtimeProperties.getFromLocalPart() + "@" + domain.domainName();
            String unsubscribeUrl = unsubscribeTokenService.buildUnsubscribeUrl(
                    campaign.tenantId(), messageJob.recipientEmail(), campaign.id());

            MimeMessage base = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(base, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(messageJob.recipientEmail());
            helper.setSubject(campaign.subject());
            helper.setText(campaign.body(), campaign.body());
            base.setHeader("List-Unsubscribe", "<" + unsubscribeUrl + ">");
            base.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");

            MimeMessage toSend = base;
            if (domain.dkimPrivateKeyPem() != null && !domain.dkimPrivateKeyPem().isBlank()) {
                RSAPrivateKey privateKey = loadPrivateKey(domain.dkimPrivateKeyPem());
                DkimSigner signer = new DkimSigner(domain.domainName(), domain.dkimSelector(), privateKey);
                toSend = new DkimMessage(base, signer);
            }

            mailSender.send(toSend);
            return "SMTP_SENT";
        } catch (MessagingException | GeneralSecurityException | DkimException e) {
            return "SMTP_FAILED: " + e.getMessage();
        } catch (MailSendException ex) {
            // SendFailedException with invalid addresses = 5xx permanent rejection (hard bounce)
            boolean hardBounce = ex.getCause() instanceof SendFailedException sfe
                    && sfe.getInvalidAddresses() != null
                    && sfe.getInvalidAddresses().length > 0;
            return (hardBounce ? "SMTP_HARD_BOUNCE" : "SMTP_FAILED") + ": " + ex.getMessage();
        } catch (RuntimeException ex) {
            return "SMTP_FAILED: " + ex.getMessage();
        }
    }

    private JavaMailSenderImpl buildMailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(runtimeProperties.getSmtpHost());
        sender.setPort(runtimeProperties.getSmtpPort());
        if (runtimeProperties.isSmtpAuthEnabled()) {
            sender.setUsername(runtimeProperties.getSmtpUsername());
            sender.setPassword(runtimeProperties.getSmtpPassword());
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", Boolean.toString(runtimeProperties.isSmtpAuthEnabled()));
        props.put("mail.smtp.starttls.enable", Boolean.toString(runtimeProperties.isSmtpStarttlsEnabled()));
        return sender;
    }

    private RSAPrivateKey loadPrivateKey(String pem) throws GeneralSecurityException {
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }
}
