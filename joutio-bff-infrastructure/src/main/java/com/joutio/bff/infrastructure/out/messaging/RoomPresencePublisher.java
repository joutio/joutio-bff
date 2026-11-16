package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.out.messaging.response.RoomPresenceView;
import com.joutio.matchmaking.domain.event.RoomEvent;
import com.joutio.microservice.core.domain.constant.JoutioConstants;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

/**
 * Présence d'un joueur dans un salon, poussée sur le <b>flux personnel</b> des joueurs du salon
 * ({@code /topic/notifications/{userId}}, type {@code ROOM_PRESENCE}, 2026-11-16) : le canal
 * {@code /topic/rooms/{id}} n'est écouté que depuis l'écran du salon. Hors du salon, le client
 * sait ainsi tout de suite qu'un adversaire l'attend (pastille de l'onglet Défis) ou qu'il est parti.
 *
 * <p>Les destinataires viennent de l'événement ({@code roomPlayerIds} : joueurs humains du salon, le
 * joueur concerné compris) : aucune relecture de la salle. <b>Éphémère</b> : jamais persisté, absent de
 * l'inbox ; un client déconnecté relit ses salons à la reconnexion. Groupe {@code room-notification},
 * instance-scopé comme les autres fan-out STOMP.</p>
 */
@Service
@ProcessingGroup("room-notification")
public class RoomPresencePublisher {

    static final String EVENT_TYPE = "ROOM_PRESENCE";
    private static final String DESTINATION_PREFIX = "/topic/notifications/";

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

    private void publish(String roomId, String playerId, boolean present, Instant at, Set<String> recipients) {
        if (playerId == null || JoutioConstants.SYSTEM_USER_ID.equals(playerId) || recipients == null) {
            return;
        }
        RoomPresenceView view = new RoomPresenceView(roomId, playerId, present, at);
        EventEnvelopeResponse envelope = EventEnvelopeResponse.of(roomId, 0, at, EVENT_TYPE, view);
        recipients.forEach(userId -> messagingTemplate.convertAndSend(DESTINATION_PREFIX + userId, envelope));
    }
}
