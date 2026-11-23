package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.out.messaging.mapper.GameEventResponseMapper;
import com.joutio.bff.infrastructure.out.messaging.response.GameEventResponse;
import com.joutio.game.domain.event.GameEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.SequenceNumber;
import org.axonframework.eventhandling.Timestamp;
import org.axonframework.messaging.annotation.SourceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Diffuse les événements de partie en temps réel sur {@code /topic/games/{gameId}}
 * (même contrat que l'historique REST : {@link EventEnvelopeResponse}).
 */
@Service
@ProcessingGroup("game-events")
public class GameEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(GameEventPublisher.class);
    private static final String DESTINATION_PREFIX = "/topic/games/";

    private final SimpMessagingTemplate messagingTemplate;

    public GameEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventHandler
    public void onGameEvent(GameEvent event, @SourceId String aggregateId, @SequenceNumber long sequenceNumber, @Timestamp Instant timestamp) {
        GameEventResponseMapper.toResponse(event)
                .ifPresentOrElse(
                        response -> send(event.gameId(), aggregateId, sequenceNumber, timestamp, response),
                        () -> logger.warn("Aucun DTO pour l'événement: {}", event.getClass().getSimpleName()));
    }

    private void send(String gameId, String aggregateId, long sequenceNumber, Instant timestamp, GameEventResponse payload) {
        EventEnvelopeResponse envelope = EventEnvelopeResponse.of(
                aggregateId,
                sequenceNumber,
                timestamp,
                payload.type().name(),
                payload
        );

        logger.debug("{} publié: gameId={}, seq={}", payload.type(), gameId, sequenceNumber);
        messagingTemplate.convertAndSend(DESTINATION_PREFIX + gameId, envelope);
    }
}
