package com.joutio.bff.infrastructure.out.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joutio.bff.application.PushSubscription;
import com.joutio.bff.application.WebPushGateway;
import com.joutio.bff.application.WebPushMessage;
import nl.martijndwars.webpush.Notification;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

import java.security.Security;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class WebPushSenderTest {

    /** Paire VAPID de développement (application-local.yml) — clé valide, aucun envoi réseau. */
    private static final String PUBLIC_KEY =
            "BL4_f0c1UrFgu1Hdym2XedjmA5cjTg8hDLn_AxU-MvhEf1aTauiA2Ph0_j4t89siXdF5dD4E6URhkonwHTBWxdE";
    private static final String PRIVATE_KEY = "hM9-hr1OPKWJ3lpi_bl6Hax-24ovYnB1KDORMkGvnb0";

    @Test
    void builds_push_service_with_bouncycastle_provider_registered() {
        WebPushProperties properties = new WebPushProperties(
                new WebPushProperties.Vapid(PUBLIC_KEY, PRIVATE_KEY, "mailto:dev@joutio.local"));

        assertThatCode(() -> new WebPushSender(properties, new ObjectMapper()))
                .doesNotThrowAnyException();
        assertThat(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)).isNotNull();
    }

    @Test
    void disabled_without_keys_fails_softly() {
        WebPushSender sender = new WebPushSender(new WebPushProperties(null), new ObjectMapper());
        PushSubscription subscription = new PushSubscription(
                "https://push.example/1", "user-1", "p256dh", "auth", "Chrome", Instant.now(), Instant.now());
        WebPushMessage message = new WebPushMessage(
                "notification-1", "CHALLENGE_RECEIVED", "actor-1", "Alice", "challenge-1",
                "topic-1", null, null, "/notifications");

        assertThat(sender.send(subscription, message)).isEqualTo(WebPushGateway.SendStatus.FAILED);
    }

    @Test
    void time_sensitive_notifications_are_high_urgency() {
        assertThat(WebPushSender.urgency(message("CHALLENGE_RECEIVED", "challenge-1")))
                .isEqualTo(Notification.Urgency.HIGH);
        assertThat(WebPushSender.urgency(message("ROOM_ACCEPTED", "room-1")))
                .isEqualTo(Notification.Urgency.HIGH);
        assertThat(WebPushSender.urgency(message("WIZZ", "room-1")))
                .isEqualTo(Notification.Urgency.HIGH);
        assertThat(WebPushSender.urgency(message("FOLLOW", "follow-1")))
                .isEqualTo(Notification.Urgency.NORMAL);
    }

    @Test
    void invitations_collapse_per_source_only() {
        assertThat(WebPushSender.topic(message("CHALLENGE_RECEIVED", "challenge-1")))
                .startsWith("challenge-");
        assertThat(WebPushSender.topic(message("FOLLOW", "follow-1"))).isNull();
        assertThat(WebPushSender.topic(message("CHALLENGE_RECEIVED", null))).isNull();
    }

    @Test
    void wizz_notifications_collapse_per_room() {
        assertThat(WebPushSender.topic(message("WIZZ", "room-1")))
                .startsWith("room-wizz-");
        assertThat(WebPushSender.topic(message("WIZZ", null))).isNull();
    }

    private static WebPushMessage message(String type, String sourceId) {
        return new WebPushMessage(
                "notification-1", type, "actor-1", "Alice", sourceId,
                "topic-1", null, null, "/notifications");
    }
}
