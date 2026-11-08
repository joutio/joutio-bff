package com.joutio.bff.application;

import com.joutio.notification.domain.event.NotificationEvent.NotificationCreatedEvent;
import com.joutio.notification.domain.model.NotificationType;
import com.joutio.notification.domain.model.NotificationCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@Service
public class ExpoPushService {
    private static final Logger log = LoggerFactory.getLogger(ExpoPushService.class);
    private final ExpoPushStore store;
    private final ExpoPushGateway gateway;
    private final NotificationViewService notifications;
    private final boolean enabled;

    public ExpoPushService(ExpoPushStore store, ExpoPushGateway gateway, NotificationViewService notifications,
                           @Value("${joutio.expo-push.enabled:false}") boolean enabled) {
        this.store = store;
        this.gateway = gateway;
        this.notifications = notifications;
        this.enabled = enabled;
    }

    public void register(String userId, String installationId, String token, ExpoPushStore.Platform platform, boolean rematches) {
        store.register(userId, installationId, token, platform, rematches);
    }
    public void unregister(String userId, String installationId) { store.unregister(userId, installationId); }

    public void enqueue(NotificationCreatedEvent event) {
        if (!enabled || (event.type() != NotificationType.CHALLENGE_RECEIVED && event.type() != NotificationType.WIZZ)) return;
        if (event.expiresAt() != null && !event.expiresAt().isAfter(Instant.now())) return;
        store.enqueue(event);
    }

    /** Chaque claim réserve une seule livraison pendant 60 s, sur toutes les répliques. */
    public void drain() {
        if (!enabled) return;
        for (int i = 0; i < 50; i++) {
            var delivery = store.claim(Instant.now());
            if (delivery.isEmpty()) break;
            process(delivery.get());
        }
    }

    private void process(ExpoPushStore.Delivery delivery) {
        Instant now = Instant.now();
        if (delivery.attempts() > 5) {
            store.finish(delivery.id());
            return;
        }
        try {
            // Les reçus restent consultables après expiration de l'invitation.
            if (delivery.ticket() == null) {
                if (!delivery.notice().expiresAt().isAfter(now) || !store.eligible(delivery)) {
                    store.finish(delivery.id());
                    return;
                }
                boolean roomsEnabled = notifications.preferences(delivery.notice().userId()).get(2, TimeUnit.SECONDS)
                        .stream().noneMatch(p -> p.category() == NotificationCategory.ROOM && !p.enabled());
                if (!roomsEnabled) {
                    store.finish(delivery.id());
                    return;
                }
            }
            var result = delivery.ticket() == null ? gateway.send(delivery) : gateway.receipt(delivery.ticket());
            switch (result.status()) {
                case ACCEPTED -> store.ticket(delivery.id(), result.ticket(), now.plus(15, ChronoUnit.MINUTES));
                case INVALID_TOKEN -> {
                    store.disableToken(delivery.token());
                    store.finish(delivery.id());
                }
                case DELIVERED, REJECTED -> store.finish(delivery.id());
                case RETRY -> retry(delivery, now);
            }
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Expo delivery {} failed ({})", delivery.id(), error.getClass().getSimpleName());
            retry(delivery, now);
        }
    }

    private void retry(ExpoPushStore.Delivery delivery, Instant now) {
        if (delivery.attempts() >= 5 || (delivery.ticket() == null && !delivery.notice().expiresAt().isAfter(now))) {
            store.finish(delivery.id());
        } else {
            store.retry(delivery.id(), now.plusSeconds(5L << delivery.attempts()));
        }
    }
}
