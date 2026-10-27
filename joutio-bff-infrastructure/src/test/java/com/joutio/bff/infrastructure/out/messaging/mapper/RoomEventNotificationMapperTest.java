package com.joutio.bff.infrastructure.out.messaging.mapper;

import com.joutio.bff.infrastructure.out.messaging.response.RoomNotification;
import com.joutio.matchmaking.domain.event.RoomEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RoomEventNotificationMapperTest {

    private static final Instant AT = Instant.parse("2026-10-16T10:00:00Z");

    @Test
    void wizz_mapsToRoomWizzedNotification() {
        RoomNotification notification = RoomEventNotificationMapper
                .toNotification(new RoomEvent.RoomWizzedEvent("room-1", "initiator", "opponent", AT))
                .orElseThrow();

        assertThat(notification).isInstanceOf(RoomNotification.RoomWizzedNotification.class);
        RoomNotification.RoomWizzedNotification wizz =
                (RoomNotification.RoomWizzedNotification) notification;
        assertThat(wizz.type()).isEqualTo(RoomNotification.RoomNotificationType.ROOM_WIZZED);
        assertThat(wizz.roomId()).isEqualTo("room-1");
        assertThat(wizz.playerId()).isEqualTo("initiator");
        assertThat(wizz.targetId()).isEqualTo("opponent");
        assertThat(wizz.wizzedAt()).isEqualTo(AT);
    }

    @Test
    void left_mapsToRoomLeftNotification() {
        RoomNotification notification = RoomEventNotificationMapper
                .toNotification(new RoomEvent.RoomLeftEvent("room-1", "player-1", AT))
                .orElseThrow();

        assertThat(notification.type()).isEqualTo(RoomNotification.RoomNotificationType.ROOM_LEFT);
    }
}
