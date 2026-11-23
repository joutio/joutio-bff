package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.application.IdentityLookup;
import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.in.api.response.NotificationDeletedView;
import com.joutio.bff.infrastructure.in.api.response.NotificationView;
import com.joutio.notification.domain.event.NotificationEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.SequenceNumber;
import org.axonframework.eventhandling.Timestamp;
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
    public void onCreated(NotificationEvent.NotificationCreatedEvent event,
                          @SequenceNumber long sequenceNumber,
                          @Timestamp Instant timestamp) {
        if (event.actorId() == null) {
            publishCreated(event, sequenceNumber, timestamp, false);
            return;
        }
        identityLookup.status(event.actorId())
                .exceptionally(_ -> null)
                .thenAccept(status -> publishCreated(
                        event, sequenceNumber, timestamp, status != null && status.suspended(Instant.now())));
    }

    @EventHandler
    public void onDeleted(NotificationEvent.NotificationDeletedEvent event,
                          @SequenceNumber long sequenceNumber,
                          @Timestamp Instant timestamp) {
        EventEnvelopeResponse envelope = EventEnvelopeResponse.of(
                event.notificationId(),
                sequenceNumber,
                timestamp,
                DELETED_EVENT_TYPE,
                new NotificationDeletedView(event.notificationId()));
        messagingTemplate.convertAndSend(DESTINATION_PREFIX + event.userId(), envelope);
    }

    private void publishCreated(NotificationEvent.NotificationCreatedEvent event,
                                long sequenceNumber,
                                Instant timestamp,
                                boolean actorSuspended) {
        EventEnvelopeResponse envelope = EventEnvelopeResponse.of(
                event.notificationId(),
                sequenceNumber,
                timestamp,
                event.type().name(),
                NotificationView.of(event, actorSuspended));
        messagingTemplate.convertAndSend(DESTINATION_PREFIX + event.userId(), envelope);
    }
}
