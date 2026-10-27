package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.application.IdentityLookup;
import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.in.api.response.NotificationDeletedView;
import com.joutio.bff.infrastructure.in.api.response.NotificationView;
import com.joutio.notification.domain.event.NotificationEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Diffuse les notifications personnelles sur {@code /topic/notifications/{userId}} : c'est le
 * canal de l'inbox (invitations de défi, follows, appariement prêt). Les canaux par entité
 * ({@code /topic/rooms/{id}}, {@code /topic/games/{id}}) restent gérés par leurs publishers.
 *
 * <p>La création pousse le {@link NotificationView} (type porté par {@code eventType}) ; la
 * suppression pousse un {@link NotificationDeletedView} sous {@code NOTIFICATION_DELETED} pour
 * que les autres onglets retirent la ligne.</p>
 *
 * <p>Groupe <b>instance-scopé</b> (`notification-ws`, suffixé par pod via le SDK) : chaque
 * réplique BFF ne connaît que ses propres clients STOMP et doit donc consommer <b>tous</b> les
 * événements pour les servir. Le Web Push vit dans {@link NotificationPushPublisher}, groupe
 * global (une seule notification OS, quel que soit le nombre de répliques).</p>
 */
@Service
@ProcessingGroup("notification-ws")
public class NotificationWsPublisher {

    private static final Logger logger = LoggerFactory.getLogger(NotificationWsPublisher.class);
    private static final String DESTINATION_PREFIX = "/topic/notifications/";
    private static final String DELETED_EVENT_TYPE = "NOTIFICATION_DELETED";

    private final SimpMessagingTemplate messagingTemplate;
    private final IdentityLookup identityLookup;

    public NotificationWsPublisher(SimpMessagingTemplate messagingTemplate,
                                   IdentityLookup identityLookup) {
        this.messagingTemplate = messagingTemplate;
        this.identityLookup = identityLookup;
    }

    @EventHandler
    public void onNotificationEvent(EventMessage<?> eventMessage) {
        Object payload = eventMessage.getPayload();
        boolean created = payload instanceof NotificationEvent.NotificationCreatedEvent;
        boolean deleted = payload instanceof NotificationEvent.NotificationDeletedEvent;
        if (!created && !deleted) {
            return;
        }
        if (!(eventMessage instanceof DomainEventMessage<?> domainMessage)) {
            logger.warn("Notification ignorée (sans métadonnées d'agrégat): {}",
                    payload.getClass().getSimpleName());
            return;
        }

        if (payload instanceof NotificationEvent.NotificationCreatedEvent event) {
            if (event.actorId() == null) {
                publishCreated(event, domainMessage, false);
                return;
            }
            identityLookup.status(event.actorId())
                    .exceptionally(_ -> null)
                    .thenAccept(status -> publishCreated(
                            event,
                            domainMessage,
                            status != null && status.suspended(Instant.now())));
            return;
        }
        NotificationEvent.NotificationDeletedEvent event =
                (NotificationEvent.NotificationDeletedEvent) payload;
        EventEnvelopeResponse envelope = EventEnvelopeResponse.of(
                event.notificationId(),
                domainMessage.getSequenceNumber(),
                domainMessage.getTimestamp(),
                DELETED_EVENT_TYPE,
                new NotificationDeletedView(event.notificationId()));
        messagingTemplate.convertAndSend(DESTINATION_PREFIX + event.userId(), envelope);
    }

    private void publishCreated(NotificationEvent.NotificationCreatedEvent event,
                                DomainEventMessage<?> domainMessage,
                                boolean actorSuspended) {
        EventEnvelopeResponse envelope = EventEnvelopeResponse.of(
                event.notificationId(),
                domainMessage.getSequenceNumber(),
                domainMessage.getTimestamp(),
                event.type().name(),
                NotificationView.of(event, actorSuspended));
        messagingTemplate.convertAndSend(DESTINATION_PREFIX + event.userId(), envelope);
    }
}
