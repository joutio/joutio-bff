package com.joutio.bff.infrastructure.out.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joutio.bff.application.PushSubscription;
import com.joutio.bff.application.WebPushGateway;
import com.joutio.bff.application.WebPushMessage;
import com.joutio.notification.domain.model.NotificationType;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.jwt.nimbus.NimbusJwtFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Envoi Web Push (RFC 8030/8291) via {@code dev.blanke.webpush} : JWT VAPID signé Nimbus,
 * chiffrement BouncyCastle, transport {@link java.net.http.HttpClient}.
 */
@Component
public class WebPushSender implements WebPushGateway {

    static {
        // La lib charge les clés VAPID via le provider BouncyCastle explicite (`BC`) : sans
        // enregistrement dans la JVM, `withVapidPublicKey` échoue au démarrage (push désactivé).
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(WebPushSender.class);
    private static final int DEFAULT_TTL_SECONDS = 43_200;
    private static final int MIN_TTL_SECONDS = 60;
    private static final int MAX_TTL_SECONDS = 86_400;
    /** Notifications time-sensitive : haute priorité, sinon FCM diffère le push en Doze. */
    private static final Set<String> URGENT_TYPES = Set.of(
            NotificationType.CHALLENGE_RECEIVED.name(),
            NotificationType.ROOM_ACCEPTED.name(),
            NotificationType.WIZZ.name());
    /** Invitations d'un même défi : collapsées par le header Topic. */
    private static final Set<String> INVITATION_TYPES = Set.of(
            NotificationType.CHALLENGE_RECEIVED.name());
    /** Relances d'une même salle : collapsées aussi (une seule notification OS par salle). */
    private static final Set<String> WIZZ_TYPES = Set.of(
            NotificationType.WIZZ.name());

    private final ObjectMapper objectMapper;
    private final PushService pushService;

    public WebPushSender(WebPushProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        if (properties.configured()) {
            this.pushService = buildPushService(properties);
        } else {
            logger.info("Web Push désactivé : clés VAPID absentes (joutio.push.vapid.*)");
            this.pushService = null;
        }
    }

    @Override
    public SendStatus send(PushSubscription subscription, WebPushMessage message) {
        if (pushService == null) {
            return SendStatus.FAILED;
        }
        try {
            Notification.Builder builder = Notification.builder()
                    .endpoint(subscription.endpoint())
                    .userPublicKey(subscription.p256dh())
                    .userAuth(subscription.auth())
                    .payload(objectMapper.writeValueAsString(message))
                    .ttl(ttlSeconds(message))
                    .urgency(urgency(message));
            String topic = topic(message);
            if (topic != null) {
                builder.topic(topic);
            }

            HttpResponse<Void> response = pushService.send(builder.build());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return SendStatus.DELIVERED;
            }
            if (status == 404 || status == 410) {
                return SendStatus.GONE;
            }
            logger.warn("Web Push {} refusé (HTTP {}) pour {}", message.type(), status, subscription.endpoint());
            return SendStatus.FAILED;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return SendStatus.FAILED;
        } catch (Exception e) {
            logger.warn("Web Push {} en échec pour {}: {}", message.type(), subscription.endpoint(), e.toString());
            return SendStatus.FAILED;
        }
    }

    private static PushService buildPushService(WebPushProperties properties) {
        try {
            return PushService.builder()
                    .withVapidPublicKey(properties.vapid().publicKey())
                    .withVapidPrivateKey(properties.vapid().privateKey())
                    .withVapidSubject(properties.vapid().subject())
                    .withJwtFactory(new NimbusJwtFactory())
                    .build();
        } catch (GeneralSecurityException e) {
            logger.error("Clés VAPID invalides : Web Push désactivé", e);
            return null;
        }
    }

    private static int ttlSeconds(WebPushMessage message) {
        if (message.expiresAt() == null) {
            return DEFAULT_TTL_SECONDS;
        }
        long seconds = Duration.between(Instant.now(), message.expiresAt()).getSeconds();
        return (int) Math.max(MIN_TTL_SECONDS, Math.min(MAX_TTL_SECONDS, seconds));
    }

    static Notification.Urgency urgency(WebPushMessage message) {
        return URGENT_TYPES.contains(message.type())
                ? Notification.Urgency.HIGH
                : Notification.Urgency.NORMAL;
    }

    /** Collapse des invitations d'un même défi / relances d'une même salle — header Topic ≤ 32 car. */
    static String topic(WebPushMessage message) {
        if (message.sourceId() == null) {
            return null;
        }
        if (INVITATION_TYPES.contains(message.type())) {
            return "challenge-" + Integer.toHexString(message.sourceId().hashCode());
        }
        if (WIZZ_TYPES.contains(message.type())) {
            return "room-wizz-" + Integer.toHexString(message.sourceId().hashCode());
        }
        return null;
    }
}
