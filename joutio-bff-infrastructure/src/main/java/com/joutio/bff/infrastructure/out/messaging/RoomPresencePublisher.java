package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.in.websocket.RoomPresenceChannelConfiguration;
import com.joutio.bff.infrastructure.out.messaging.response.RoomPresenceView;
import com.joutio.bff.infrastructure.out.messaging.response.RoomStatusView;
import com.joutio.matchmaking.domain.event.RoomEvent;
import com.joutio.matchmaking.domain.model.RoomStatus;
import com.joutio.microservice.core.domain.constant.JoutioConstants;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Flux personnel des salles ({@code /topic/room-presence/{userId}}) : le canal {@code /topic/rooms/{id}} n'est écouté
 * que depuis l'écran du salon. Hors du salon, le client apprend ainsi tout de suite :
 * <ul>
 *   <li>{@code ROOM_PRESENCE} : un adversaire l'attend (pastille de l'onglet Défis) ou est parti ;</li>
 *   <li>{@code ROOM_STATUS} : une salle change d'étape (défi reçu ou envoyé, accepté, refusé, annulé, expiré, partie
 *       créée). Indépendant de l'inbox et des préférences de notification.</li>
 * </ul>
 *
 * <p>Les destinataires viennent de l'événement ({@code roomPlayerIds} : joueurs humains du salon, le
 * joueur concerné compris) : aucune relecture de la salle. <b>Éphémère</b> : jamais persisté, absent de
 * l'inbox ; un client déconnecté relit ses salons à la reconnexion. Groupe {@code room-events},
 * instance-scopé comme les autres fan-out STOMP.</p>
 */
@Service
@ProcessingGroup("room-events")
public class RoomPresencePublisher {

    static final String EVENT_TYPE = "ROOM_PRESENCE";
    static final String STATUS_EVENT_TYPE = "ROOM_STATUS";

    private final SimpMessagingTemplate messagingTemplate;

    public RoomPresencePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventHandler
    public void onEntered(RoomEvent.RoomEnteredEvent event) {
        publish(event.roomId(), event.playerId(), true, event.enteredAt(), event.roomPlayerIds());
    }

    @EventHandler
    public void onLeft(RoomEvent.RoomLeftEvent event) {
        publish(event.roomId(), event.playerId(), false, event.leftAt(), event.roomPlayerIds());
    }

    @EventHandler
    public void onCreated(RoomEvent.RoomCreatedEvent event) {
        publishStatus(event.roomId(), event.status(), event.expiresAt(), event.createdAt(),
                humans(event.initiatorId(), event.opponentId()));
    }

    @EventHandler
    public void onAccepted(RoomEvent.RoomAcceptedEvent event) {
        publishStatus(event.roomId(), RoomStatus.WAITING_PLAYERS, event.expiresAt(), event.acceptedAt(),
                humans(event.initiatorId(), event.opponentId()));
    }

    @EventHandler
    public void onDeclined(RoomEvent.RoomDeclinedEvent event) {
        publishStatus(event.roomId(), RoomStatus.DECLINED, null, event.declinedAt(),
                humans(event.initiatorId(), event.opponentId()));
    }

    @EventHandler
    public void onCancelled(RoomEvent.RoomCancelledEvent event) {
        publishStatus(event.roomId(), RoomStatus.CANCELLED, null, event.cancelledAt(), event.roomPlayerIds());
    }

    @EventHandler
    public void onExpired(RoomEvent.RoomExpiredEvent event) {
        publishStatus(event.roomId(), RoomStatus.EXPIRED, null, event.expiredAt(), event.roomPlayerIds());
    }

    @EventHandler
    public void onCompleted(RoomEvent.RoomCompletedEvent event) {
        publishStatus(event.roomId(), RoomStatus.COMPLETED, null, event.completedAt(), event.roomPlayerIds());
    }

    private void publishStatus(String roomId, RoomStatus status, Instant expiresAt, Instant at, Set<String> recipients) {
        if (recipients == null) {
            return;
        }
        EventEnvelopeResponse envelope = EventEnvelopeResponse.of(
                roomId, 0, at, STATUS_EVENT_TYPE, new RoomStatusView(roomId, status, expiresAt, at));
        recipients.forEach(userId -> messagingTemplate.convertAndSend(
                RoomPresenceChannelConfiguration.DESTINATION_PREFIX + userId, envelope));
    }

    private static Set<String> humans(String... ids) {
        return Stream.of(ids)
                .filter(id -> id != null && !JoutioConstants.SYSTEM_USER_ID.equals(id))
                .collect(Collectors.toUnmodifiableSet());
    }

    private void publish(String roomId, String playerId, boolean present, Instant at, Set<String> recipients) {
        if (playerId == null || JoutioConstants.SYSTEM_USER_ID.equals(playerId) || recipients == null) {
            return;
        }
        RoomPresenceView view = new RoomPresenceView(roomId, playerId, present, at);
        EventEnvelopeResponse envelope = EventEnvelopeResponse.of(roomId, 0, at, EVENT_TYPE, view);
        recipients.forEach(userId -> messagingTemplate.convertAndSend(RoomPresenceChannelConfiguration.DESTINATION_PREFIX + userId, envelope));
    }
}
