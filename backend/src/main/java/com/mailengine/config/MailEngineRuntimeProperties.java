package com.mailengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mail-engine.runtime")
public class MailEngineRuntimeProperties {

    private DeliveryMode deliveryMode = DeliveryMode.LOCAL_OUTBOX;
    private String storageMode = "in-memory";
    private String smtpRelayMode = "disabled";
    private String smtpHost = "localhost";
    private int smtpPort = 1025;
    private String smtpUsername = "";
    private String smtpPassword = "";
    private boolean smtpStarttlsEnabled = false;
    private boolean smtpAuthEnabled = false;
    private String fromLocalPart = "no-reply";
    private String dkimSelector = "me1";
    private String spfRecordValue = "v=spf1 mx ~all";
    private String dmarcPolicy = "none";

    public DeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(DeliveryMode deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public String getStorageMode() {
        return storageMode;
    }

    public void setStorageMode(String storageMode) {
        this.storageMode = storageMode;
    }

    public String getSmtpRelayMode() {
        return smtpRelayMode;
    }

    public void setSmtpRelayMode(String smtpRelayMode) {
        this.smtpRelayMode = smtpRelayMode;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public int getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(int smtpPort) {
        this.smtpPort = smtpPort;
    }

    public String getSmtpUsername() {
        return smtpUsername;
    }

    public void setSmtpUsername(String smtpUsername) {
        this.smtpUsername = smtpUsername;
    }

    public String getSmtpPassword() {
        return smtpPassword;
    }

    public void setSmtpPassword(String smtpPassword) {
        this.smtpPassword = smtpPassword;
    }

    public boolean isSmtpStarttlsEnabled() {
        return smtpStarttlsEnabled;
    }

    public void setSmtpStarttlsEnabled(boolean smtpStarttlsEnabled) {
        this.smtpStarttlsEnabled = smtpStarttlsEnabled;
    }

    public boolean isSmtpAuthEnabled() {
        return smtpAuthEnabled;
    }

    public void setSmtpAuthEnabled(boolean smtpAuthEnabled) {
        this.smtpAuthEnabled = smtpAuthEnabled;
    }

    public String getFromLocalPart() {
        return fromLocalPart;
    }

    public void setFromLocalPart(String fromLocalPart) {
        this.fromLocalPart = fromLocalPart;
    }

    public String getDkimSelector() {
        return dkimSelector;
    }

    public void setDkimSelector(String dkimSelector) {
        this.dkimSelector = dkimSelector;
    }

    public String getSpfRecordValue() {
        return spfRecordValue;
    }

    public void setSpfRecordValue(String spfRecordValue) {
        this.spfRecordValue = spfRecordValue;
    }

    public String getDmarcPolicy() {
        return dmarcPolicy;
    }

    public void setDmarcPolicy(String dmarcPolicy) {
        this.dmarcPolicy = dmarcPolicy;
    }

    public enum DeliveryMode {
        LOCAL_OUTBOX,
        AWS_SMTP_RELAY,
        SMTP
    }
}
