package com.mailengine.service;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class DkimKeyGenerator {

    public DkimKeyMaterial generate(String selector) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();

            String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            String privateKey = Base64.getMimeEncoder(64, "\n".getBytes())
                    .encodeToString(keyPair.getPrivate().getEncoded());
            String privateKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                    + privateKey
                    + "\n-----END PRIVATE KEY-----";
            return new DkimKeyMaterial(selector, publicKey, privateKeyPem);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to generate DKIM key pair", ex);
        }
    }
}
