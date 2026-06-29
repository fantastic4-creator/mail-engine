package com.mailengine.delivery;

import com.mailengine.config.MailEngineRuntimeProperties;
import com.mailengine.data.PlatformStateStore;
import com.mailengine.domain.Campaign;
import com.mailengine.domain.MessageJob;
import com.mailengine.domain.OutboundIp;
import com.mailengine.domain.OutboundMessage;
import com.mailengine.domain.SendingDomain;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component
public class ConfigurableDeliveryGateway implements DeliveryGateway {

    private final MailEngineRuntimeProperties runtimeProperties;
    private final PlatformStateStore store;

    public ConfigurableDeliveryGateway(MailEngineRuntimeProperties runtimeProperties, PlatformStateStore store) {
        this.runtimeProperties = runtimeProperties;
        this.store = store;
    }

    @Override
    public OutboundMessage deliver(Campaign campaign, MessageJob messageJob, OutboundIp outboundIp) {
        String status = switch (runtimeProperties.getDeliveryMode()) {
            case LOCAL_OUTBOX -> "LOCAL_CAPTURED";
            case AWS_SMTP_RELAY -> "QUEUED_FOR_SMTP_RELAY";
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
                campaign.body(),
                status,
                Instant.now()
        );
        return store.saveOutboundMessage(message);
    }

    private String sendSmtp(Campaign campaign, MessageJob messageJob) {
        SendingDomain domain = store.findDomain(campaign.domainId())
                .orElseThrow(() -> new IllegalStateException("Campaign domain not found"));
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(runtimeProperties.getSmtpHost());
        mailSender.setPort(runtimeProperties.getSmtpPort());
        if (runtimeProperties.isSmtpAuthEnabled()) {
            mailSender.setUsername(runtimeProperties.getSmtpUsername());
            mailSender.setPassword(runtimeProperties.getSmtpPassword());
        }

        Properties javaMailProperties = mailSender.getJavaMailProperties();
        javaMailProperties.put("mail.smtp.auth", Boolean.toString(runtimeProperties.isSmtpAuthEnabled()));
        javaMailProperties.put("mail.smtp.starttls.enable", Boolean.toString(runtimeProperties.isSmtpStarttlsEnabled()));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(runtimeProperties.getFromLocalPart() + "@" + domain.domainName());
        message.setTo(messageJob.recipientEmail());
        message.setSubject(campaign.subject());
        message.setText(campaign.body());

        try {
            mailSender.send(message);
            return "SMTP_SENT";
        } catch (RuntimeException ex) {
            return "SMTP_FAILED: " + ex.getMessage();
        }
    }
}
