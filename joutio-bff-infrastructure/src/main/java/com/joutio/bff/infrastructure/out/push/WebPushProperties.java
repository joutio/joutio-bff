package com.joutio.bff.infrastructure.out.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration Web Push (clés VAPID). Propriétés : {@code joutio.push.vapid.public-key},
 * {@code joutio.push.vapid.private-key}, {@code joutio.push.vapid.subject} (mailto: ou https:).
 * Sans clés, le Web Push est silencieusement désactivé (le STOMP reste la voie nominale).
 */
@ConfigurationProperties(prefix = "joutio.push")
public record WebPushProperties(Vapid vapid) {

    public WebPushProperties {
        if (vapid == null) {
            vapid = new Vapid(null, null, null);
        }
    }

    public record Vapid(String publicKey, String privateKey, String subject) {
    }

    public boolean configured() {
        return hasText(vapid.publicKey()) && hasText(vapid.privateKey()) && hasText(vapid.subject());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
