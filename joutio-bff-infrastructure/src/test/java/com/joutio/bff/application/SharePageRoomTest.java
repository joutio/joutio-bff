package com.joutio.bff.application;

import com.joutio.matchmaking.domain.exception.RoomExceptions;
import com.joutio.matchmaking.domain.model.Room;
import com.joutio.matchmaking.domain.model.RoomStatus;
import com.joutio.matchmaking.domain.query.RoomQuery;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Page d'un lien de salon : invitation tant que le salon attend, « expiré ou introuvable » sinon. */
class SharePageRoomTest {

    private final QueryGateway queryGateway = mock(QueryGateway.class);
    private final SharePageService pages = new SharePageService(
            mock(ProfileLookup.class), mock(IdentityLookup.class), queryGateway,
            new ShareProperties("https://app.joutio.com", "store", "com.joutio.app", List.of(), List.of(), "og.png"));

    @Test
    void waitingRoom_invitesToJoin() {
        stubRoom(CompletableFuture.completedFuture(Room.builder().roomId("r1").status(RoomStatus.WAITING_PLAYERS).build()));

        SharePageService.SharePage page = pages.room("r1").join();

        assertThat(page.title()).isEqualTo("Un défi t'attend sur Joutio");
        assertThat(page.appPath()).isEqualTo("/join/r1");
    }

    @Test
    void finishedOrPurgedRoom_saysItIsGone() {
        stubRoom(CompletableFuture.completedFuture(Room.builder().roomId("r1").status(RoomStatus.EXPIRED).build()));
        assertThat(pages.room("r1").join().title()).isEqualTo("Salon expiré ou introuvable");

        stubRoom(CompletableFuture.failedFuture(new RoomExceptions.RoomNotFoundProblem("r1")));
        assertThat(pages.room("r1").join().title()).isEqualTo("Salon expiré ou introuvable");
    }

    @SuppressWarnings("unchecked")
    private void stubRoom(CompletableFuture<Room> room) {
        when(queryGateway.query(any(RoomQuery.GetRoomById.class), any(ResponseType.class))).thenReturn(room);
    }
}
