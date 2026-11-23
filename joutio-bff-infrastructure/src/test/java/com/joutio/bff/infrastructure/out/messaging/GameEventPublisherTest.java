package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.out.messaging.mapper.GameEventResponseMapper;
import com.joutio.game.domain.event.GameEvent;
import org.axonframework.eventhandling.AnnotationEventHandlerAdapter;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GameEventPublisherTest {

    @Test
    void axon_resolves_the_aggregate_id_sequence_and_timestamp() throws Exception {
        var template = mock(SimpMessagingTemplate.class);
        var handler = new AnnotationEventHandlerAdapter(new GameEventPublisher(template));
        var timestamp = Instant.parse("2026-11-23T10:00:00Z");
        var event = new GameEvent.GameCancelledEvent("game-1", "PLAYER_CANCELLED", timestamp.minusSeconds(1));
        var message = new GenericDomainEventMessage<>("Game", "aggregate-1", 7L, event, Map.of(), "event-1", timestamp);

        assertThat(handler.canHandle(message)).isTrue();
        assertThat(handler.canHandle(GenericEventMessage.asEventMessage(event))).isFalse();
        handler.handle(message);

        verify(template).convertAndSend("/topic/games/game-1", EventEnvelopeResponse.of(
                "aggregate-1", 7L, timestamp, "GAME_CANCELLED", GameEventResponseMapper.toResponse(event).orElseThrow()));
    }
}
