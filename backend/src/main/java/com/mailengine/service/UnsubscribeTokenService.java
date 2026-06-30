package com.mailengine.service;

import com.mailengine.config.MailEngineRuntimeProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class UnsubscribeTokenService {

    private final MailEngineRuntimeProperties runtimeProperties;

    public UnsubscribeTokenService(MailEngineRuntimeProperties runtimeProperties) {
        this.runtimeProperties = runtimeProperties;
    }

    public String generateToken(UUID tenantId, String email, UUID campaignId) {
        return hmac(tenantId + ":" + email + ":" + campaignId);
    }

    public boolean validateToken(UUID tenantId, String email, UUID campaignId, String sig) {
        String expected = generateToken(tenantId, email, campaignId);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8));
    }

    public String buildUnsubscribeUrl(UUID tenantId, String email, UUID campaignId) {
        String token = generateToken(tenantId, email, campaignId);
        String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
        return runtimeProperties.getAppBaseUrl()
                + "/unsubscribe?tenant=" + tenantId
                + "&email=" + encodedEmail
                + "&campaign=" + campaignId
                + "&sig=" + token;
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    runtimeProperties.getUnsubscribeHmacSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256");
            mac.init(keySpec);
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }
}
