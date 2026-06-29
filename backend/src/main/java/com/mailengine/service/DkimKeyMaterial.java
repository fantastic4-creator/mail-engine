package com.mailengine.service;

public record DkimKeyMaterial(
        String selector,
        String publicKey,
        String privateKeyPem
) {
}
