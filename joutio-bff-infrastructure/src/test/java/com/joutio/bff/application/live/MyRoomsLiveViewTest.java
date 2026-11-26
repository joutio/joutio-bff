package com.joutio.bff.application.live;

import com.joutio.bff.application.RoomViewService;
import com.joutio.bff.infrastructure.in.api.response.RoomView;
import com.joutio.matchmaking.domain.model.MyRooms;
import com.joutio.matchmaking.domain.model.Room;
import com.joutio.matchmaking.domain.query.RoomQuery.GetMyRooms;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyRoomsLiveViewTest {

    private final RoomViewService rooms = mock(RoomViewService.class);
    private final MyRoomsLiveView view = new MyRoomsLiveView(rooms);

    @Test
    void only_the_player_of_the_key_subscribes() {
        assertThat(view.canSubscribe("u1", "u1")).isTrue();
        assertThat(view.canSubscribe("u2", "u1")).isFalse();
        assertThat(view.canSubscribe(null, "u1")).isFalse();
        assertThat(view.destinationPrefix()).isEqualTo("/topic/live/my-rooms/");
        assertThat(view.query("u1")).isEqualTo(new GetMyRooms("u1"));
    }

    @Test
    void pushes_the_rooms_seen_by_the_player_under_the_version_of_matchmaking() {
        Room room = Room.builder().roomId("r1").initiatorId("u1").build();
        RoomView seen = mock(RoomView.class);
        when(rooms.views(List.of(room), "u1")).thenReturn(CompletableFuture.completedFuture(List.of(seen)));

        LiveUpdate<List<RoomView>> update = view.message("u1", new MyRooms("u1", 9, List.of(room))).join();

        assertThat(update).isEqualTo(new LiveUpdate<>("my-rooms", "u1", 9, List.of(seen)));
    }
}
