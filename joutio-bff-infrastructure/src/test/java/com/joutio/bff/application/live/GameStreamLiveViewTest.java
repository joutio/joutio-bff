package com.joutio.bff.application.live;

import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.out.messaging.mapper.GameEventResponseMapper;
import com.joutio.game.domain.event.GameEvent;
import com.joutio.game.domain.query.GameQuery.GetGameEventsQuery;
import com.joutio.microservice.core.domain.model.event.EventEnvelope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameStreamLiveViewTest {

    private static final Instant AT = Instant.parse("2026-11-25T10:00:00Z");

    private final GameStreamLiveView view = new GameStreamLiveView();

    @Test
    void serves_the_existing_game_destination_to_any_connected_player() {
        assertThat(view.destinationPrefix()).isEqualTo("/topic/games/");
        assertThat(view.canSubscribe("u1", "g1")).isTrue();
        assertThat(view.canSubscribe(null, "g1")).isFalse();
        assertThat(view.query("g1")).isEqualTo(new GetGameEventsQuery("g1"));
    }

    @Test
    void pushes_each_event_in_the_contract_of_the_rest_history() {
        GameEvent event = new GameEvent.GameCancelledEvent("g1", "PLAYER_CANCELLED", AT);
        EventEnvelope envelope = EventEnvelope.of("g1", 7, AT, event);

        assertThat(view.version(envelope)).isEqualTo(7);
        assertThat(view.updatesOf(List.of(envelope))).containsExactly(envelope);
        assertThat(view.message("g1", envelope)).isEqualTo(EventEnvelopeResponse.of(
                "g1", 7, AT, "GAME_CANCELLED", GameEventResponseMapper.toResponse(event).orElseThrow()));
    }

    @Test
    void pushes_nothing_for_a_payload_outside_the_game_contract() {
        assertThat(view.message("g1", EventEnvelope.of("g1", 8, AT, "unknown"))).isNull();
    }
}
