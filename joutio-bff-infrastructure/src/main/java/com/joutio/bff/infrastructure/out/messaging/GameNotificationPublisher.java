package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.out.messaging.mapper.GameEventNotificationMapper;
import com.joutio.bff.infrastructure.out.messaging.response.GameNotification;
import com.joutio.game.domain.event.GameEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Diffuse les événements de partie en temps réel sur {@code /topic/games/{gameId}}
 * (même contrat que l'historique REST : {@link EventEnvelopeResponse}).
 */
@Service
@ProcessingGroup("game-notification")
public class GameNotificationPublisher {

    private static final Logger logger = LoggerFactory.getLogger(GameNotificationPublisher.class);
    private static final String DESTINATION_PREFIX = "/topic/games/";

    private final SimpMessagingTemplate messagingTemplate;

    public GameNotificationPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventHandler
    public void onGameEvent(EventMessage<?> eventMessage) {
        if (!(eventMessage.getPayload() instanceof GameEvent event)) {
            return;
        }
        GameEventNotificationMapper.toNotification(event)
                .ifPresentOrElse(
                        notification -> send(event, eventMessage, notification),
                        () -> logger.warn("Aucun mapping de notification pour l'événement: {}", event.getClass().getSimpleName()));
    }

    private void send(GameEvent event, EventMessage<?> eventMessage, GameNotification payload) {
        if (!(eventMessage instanceof DomainEventMessage<?> domainMessage)) {
            logger.warn("Notification ignorée (événement sans métadonnées d'agrégat): {}", event.getClass().getSimpleName());
            return;
        }

        EventEnvelopeResponse envelope = EventEnvelopeResponse.of(
                domainMessage.getAggregateIdentifier(),
                domainMessage.getSequenceNumber(),
                domainMessage.getTimestamp(),
                payload.type().name(),
                payload
        );

        logger.debug("{} publié: gameId={}, seq={}", payload.type(), event.gameId(), domainMessage.getSequenceNumber());
        messagingTemplate.convertAndSend(DESTINATION_PREFIX + event.gameId(), envelope);
    }
}
