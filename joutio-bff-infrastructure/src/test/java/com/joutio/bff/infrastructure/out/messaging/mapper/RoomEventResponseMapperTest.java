package com.joutio.bff.infrastructure.out.messaging.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.joutio.bff.infrastructure.out.messaging.response.RoomEventResponse;
import com.joutio.matchmaking.domain.event.RoomEvent;
import com.joutio.matchmaking.domain.model.RoomOrigin;
import com.joutio.matchmaking.domain.model.RoomReminderStage;
import com.joutio.matchmaking.domain.model.RoomStatus;

import org.junit.jupiter.api.Test;

import java.time.Instant;

class RoomEventResponseMapperTest {

    private static final Instant AT = Instant.parse("2026-10-16T10:00:00Z");

    @Test
    void reminder_mapsToRoomRemindedEventResponse() {
        RoomEventResponse response = RoomEventResponseMapper.toResponse(new RoomEvent.RoomRemindedEvent(
                        "room-1", "initiator", "opponent", RoomReminderStage.PRESENCE, "topic-1", null,
                        AT.plusSeconds(3600), AT, AT.plusSeconds(10)))
                .orElseThrow();

        assertThat(response).isInstanceOf(RoomEventResponse.RoomRemindedEventResponse.class);
        RoomEventResponse.RoomRemindedEventResponse reminded = (RoomEventResponse.RoomRemindedEventResponse) response;
        assertThat(reminded.type()).isEqualTo(RoomEventResponse.RoomEventResponseType.ROOM_REMINDED);
        assertThat(reminded.playerId()).isEqualTo("initiator");
        assertThat(reminded.targetId()).isEqualTo("opponent");
        assertThat(reminded.stage()).isEqualTo(RoomReminderStage.PRESENCE);
        assertThat(reminded.remindableAt()).isEqualTo(AT.plusSeconds(10));
    }

    @Test
    void invitation_isAnnouncedWithItsStatus_thenAcceptedOrDeclined() {
        var created = (RoomEventResponse.RoomCreatedEventResponse) RoomEventResponseMapper.toResponse(
                        new RoomEvent.RoomCreatedEvent("room-1", "topic-1", "initiator", "opponent", false, null,
                                RoomOrigin.REMATCH, "game-0", RoomStatus.INVITED, AT.plusSeconds(10), AT))
                .orElseThrow();
        assertThat(created.status()).isEqualTo(RoomStatus.INVITED);
        assertThat(created.sourceGameId()).isEqualTo("game-0");

        assertThat(RoomEventResponseMapper.toResponse(new RoomEvent.RoomAcceptedEvent(
                        "room-1", "topic-1", "initiator", "opponent", RoomOrigin.REMATCH, AT.plusSeconds(86_400), AT))
                .orElseThrow().type()).isEqualTo(RoomEventResponse.RoomEventResponseType.ROOM_ACCEPTED);
        assertThat(RoomEventResponseMapper.toResponse(new RoomEvent.RoomDeclinedEvent(
                        "room-1", "topic-1", "initiator", "opponent", RoomOrigin.REMATCH, AT))
                .orElseThrow().type()).isEqualTo(RoomEventResponse.RoomEventResponseType.ROOM_DECLINED);
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
