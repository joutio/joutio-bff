package com.joutio.bff.application;

import com.joutio.notification.domain.event.NotificationEvent.NotificationCreatedEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Port de persistance des installations et de la file durable Expo. */
public interface ExpoPushStore {
    enum Platform { ANDROID, IOS }
    record Notice(String notificationId, String userId, String type, String sourceId,
                  String gameId, Instant expiresAt) {}
    record Delivery(String id, String installationId, String token, Notice notice,
                    String ticket, int attempts) {}
    void register(String userId, String installationId, String token, Platform platform, boolean rematches);
    void unregister(String userId, String installationId);
    void deleteUser(String userId);
    void enqueue(NotificationCreatedEvent event);
    Optional<Delivery> claim(Instant now);
    boolean eligible(Delivery delivery);
    void finish(String id);
    void retry(String id, Instant next);
    void ticket(String id, String ticket, Instant checkAt);
    void disableToken(String token);
    void cancel(String notificationId);
    void purge(Instant before);
}
