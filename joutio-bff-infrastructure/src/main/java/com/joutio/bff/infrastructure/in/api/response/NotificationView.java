package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.notification.domain.event.NotificationEvent;
import com.joutio.notification.domain.model.Notification;
import com.joutio.notification.domain.model.NotificationType;

import java.time.Instant;

/**
 * Vue d'une notification personnelle (inbox). Les identifiants sont résolus côté client
 * (profil/topic déjà en cache) ; le push WS et l'historique REST partagent ce contrat.
 */
public record NotificationView(
        String notificationId,
        NotificationType type,
        String actorId,
        String sourceId,
        String topicId,
        String gameId,
        Instant expiresAt,
        Instant readAt,
        Instant createdAt,
        boolean actorSuspended
) {

    public static NotificationView of(Notification notification) {
        return of(notification, false);
    }

    public static NotificationView of(Notification notification, boolean actorSuspended) {
        return new NotificationView(
                notification.notificationId(),
                notification.type(),
                notification.actorId(),
                notification.sourceId(),
                notification.topicId(),
                notification.gameId(),
                notification.expiresAt(),
                notification.readAt(),
                notification.createdAt(),
                actorSuspended);
    }

    public static NotificationView of(NotificationEvent.NotificationCreatedEvent event) {
        return of(event, false);
    }

    public static NotificationView of(NotificationEvent.NotificationCreatedEvent event, boolean actorSuspended) {
        return new NotificationView(
                event.notificationId(),
                event.type(),
                event.actorId(),
                event.sourceId(),
                event.topicId(),
                event.gameId(),
                event.expiresAt(),
                null,
                event.createdAt(),
                actorSuspended);
    }
}
