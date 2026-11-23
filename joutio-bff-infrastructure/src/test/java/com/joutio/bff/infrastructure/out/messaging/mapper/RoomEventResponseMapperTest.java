package com.joutio.bff.infrastructure.out.messaging.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.joutio.bff.infrastructure.out.messaging.response.RoomEventResponse;
import com.joutio.matchmaking.domain.event.RoomEvent;

import org.junit.jupiter.api.Test;

import java.time.Instant;

class RoomEventResponseMapperTest {

    private static final Instant AT = Instant.parse("2026-10-16T10:00:00Z");

    @Test
    void wizz_mapsToRoomWizzedEventResponse() {
        RoomEventResponse response =
                RoomEventResponseMapper.toResponse(
                                new RoomEvent.RoomWizzedEvent(
                                        "room-1", "initiator", "opponent", AT))
                        .orElseThrow();

        assertThat(response).isInstanceOf(RoomEventResponse.RoomWizzedEventResponse.class);
        RoomEventResponse.RoomWizzedEventResponse wizz =
                (RoomEventResponse.RoomWizzedEventResponse) response;
        assertThat(wizz.type()).isEqualTo(RoomEventResponse.RoomEventResponseType.ROOM_WIZZED);
        assertThat(wizz.roomId()).isEqualTo("room-1");
        assertThat(wizz.playerId()).isEqualTo("initiator");
        assertThat(wizz.targetId()).isEqualTo("opponent");
        assertThat(wizz.wizzedAt()).isEqualTo(AT);
    }

    @Test
    void left_mapsToRoomLeftEventResponse() {
        RoomEventResponse response =
                RoomEventResponseMapper.toResponse(
                                new RoomEvent.RoomLeftEvent("room-1", "player-1", AT, null, null, java.util.Set.of()))
                        .orElseThrow();

        assertThat(response.type()).isEqualTo(RoomEventResponse.RoomEventResponseType.ROOM_LEFT);
    }

    @Test
    void availabilityConfirmationKeepsEventSequenceContiguous() {
        var response =
                RoomEventResponseMapper.toResponse(
                                new RoomEvent.RoomTopicAvailabilityConfirmedEvent("room-1", AT))
                        .orElseThrow();
        assertThat(response.type())
                .isEqualTo(RoomEventResponse.RoomEventResponseType.ROOM_QUESTIONS_LOADED);
    }
}
