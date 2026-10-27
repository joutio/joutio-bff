package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.response.NotificationPreferenceView;
import com.joutio.bff.infrastructure.in.api.response.NotificationView;
import com.joutio.bff.infrastructure.in.api.response.PageResponse;
import com.joutio.identity.domain.model.UserAccountStatus;
import com.joutio.microservice.core.domain.exception.BaseProblem;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.notification.domain.command.NotificationCommand;
import com.joutio.notification.domain.exception.NotificationExceptions;
import com.joutio.notification.domain.model.Notification;
import com.joutio.notification.domain.model.NotificationCategory;
import com.joutio.notification.domain.model.NotificationPage;
import com.joutio.notification.domain.model.NotificationPreference;
import com.joutio.notification.domain.query.NotificationQuery;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Facade de l'inbox : lecture paginée, compteur, transitions de lecture, suppression, préférences. */
@Service
public class NotificationViewService {

    private final QueryGateway queryGateway;
    private final CommandGateway commandGateway;
    private final IdentityLookup identityLookup;

    public NotificationViewService(QueryGateway queryGateway,
                                   CommandGateway commandGateway,
                                   IdentityLookup identityLookup) {
        this.queryGateway = queryGateway;
        this.commandGateway = commandGateway;
        this.identityLookup = identityLookup;
    }

    public CompletableFuture<PageResponse<NotificationView>> list(String userId,
                                                                  boolean unreadOnly,
                                                                  int page,
                                                                  int size) {
        return queryGateway
                .query(new NotificationQuery.GetNotificationsQuery(userId, unreadOnly, page, size),
                        QueryResponseTypes.instanceOf(NotificationPage.class))
                .thenCompose(result -> {
                    List<String> actorIds = result.content().stream()
                            .map(Notification::actorId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();
                    CompletableFuture<Map<String, UserAccountStatus>> statusesFuture =
                            identityLookup.statuses(actorIds)
                                    .exceptionally(_ -> Map.<String, UserAccountStatus>of());
                    return statusesFuture.thenApply(statusById -> PageResponse.of(
                            result.content().stream()
                                    .map(notification -> NotificationView.of(
                                            notification,
                                            suspended(statusById, notification.actorId())))
                                    .toList(),
                            page,
                            size,
                            result.totalElements()));
                });
    }

    public CompletableFuture<Long> unreadCount(String userId) {
        return queryGateway
                .query(new NotificationQuery.CountUnreadNotificationsQuery(userId),
                        QueryResponseTypes.instanceOf(Long.class));
    }

    /**
     * Transitions unitaires, sans pré-lecture du read model : la projection peut être en retard
     * juste après la réception du toast (l'agrégat, lui, est déjà créé). Le contrôle de propriétaire
     * reste dans l'agrégat ; un agrégat réellement absent est traduit en 404.
     */
    public CompletableFuture<Void> markRead(String userId, String notificationId) {
        return dispatch(notificationId,
                new NotificationCommand.MarkNotificationReadCommand(notificationId, userId));
    }

    /** Repasse une notification en non lue (swipe inversé / menu desktop). */
    public CompletableFuture<Void> markUnread(String userId, String notificationId) {
        return dispatch(notificationId,
                new NotificationCommand.MarkNotificationUnreadCommand(notificationId, userId));
    }

    /** Suppression (hard delete). Même contrôle propriétaire que la lecture, dans l'agrégat. */
    public CompletableFuture<Void> delete(String userId, String notificationId) {
        return dispatch(notificationId,
                new NotificationCommand.DeleteNotificationCommand(notificationId, userId));
    }

    public CompletableFuture<Void> markAllRead(String userId) {
        return commandGateway
                .send(new NotificationCommand.MarkAllNotificationsReadCommand(userId))
                .thenAccept(_ -> {
                });
    }

    /** Suppression de toute l'inbox (hard delete, fan-out côté joutio-notification). */
    public CompletableFuture<Void> deleteAll(String userId) {
        return commandGateway
                .send(new NotificationCommand.DeleteAllNotificationsCommand(userId))
                .thenAccept(_ -> {
                });
    }

    public CompletableFuture<List<NotificationPreferenceView>> preferences(String userId) {
        return queryGateway
                .query(new NotificationQuery.GetNotificationPreferencesQuery(userId),
                        QueryResponseTypes.multipleInstancesOf(NotificationPreference.class))
                .thenApply(preferences -> preferences.stream()
                        .map(preference -> new NotificationPreferenceView(
                                preference.category(), preference.enabled()))
                        .toList());
    }

    public CompletableFuture<Void> updatePreference(String userId,
                                                    NotificationCategory category,
                                                    boolean enabled) {
        return commandGateway
                .send(new NotificationCommand.UpdateNotificationPreferenceCommand(userId, category, enabled))
                .thenAccept(_ -> {
                });
    }

    /**
     * Envoie une commande unitaire et traduit les échecs d'agrégat en problèmes de façade.
     * Une commande peut réussir alors que le read model n'a pas encore la ligne (projection en
     * retard) : c'est précisément ce que la pré-lecture cassait.
     */
    private CompletableFuture<Void> dispatch(String notificationId, NotificationCommand command) {
        return commandGateway.send(command).handle((_ignored, error) -> {
            if (error != null) {
                throw translate(notificationId, error);
            }
            return null;
        });
    }

    private static RuntimeException translate(String notificationId, Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof BaseProblem problem) {
                return problem;
            }
            if (current instanceof AggregateNotFoundException) {
                return new NotificationExceptions.NotificationNotFoundProblem(notificationId);
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return error instanceof RuntimeException runtime ? runtime
                : new CompletionException(error);
    }

    private static boolean suspended(Map<String, UserAccountStatus> statusById, String actorId) {
        if (actorId == null) {
            return false;
        }
        UserAccountStatus status = statusById.get(actorId);
        return status != null && status.suspended(Instant.now());
    }
}
