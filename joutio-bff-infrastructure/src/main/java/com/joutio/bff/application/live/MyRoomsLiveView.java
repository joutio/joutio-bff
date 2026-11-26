package com.joutio.bff.application.live;

import com.joutio.bff.application.RoomViewService;
import com.joutio.bff.infrastructure.in.api.response.RoomView;
import com.joutio.matchmaking.domain.model.MyRooms;
import com.joutio.matchmaking.domain.query.RoomQuery.GetMyRooms;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Salles non terminales d'un joueur, {@code /topic/live/my-rooms/{userId}} : défis reçus et envoyés, salles en attente
 * (présences comprises) ou en préparation. matchmaking émet la liste à chaque écriture d'une des salles du joueur ;
 * le BFF la compose comme sa lecture REST (sujet, adversaire). Accès réservé au joueur de la clé.
 */
@Component
public class MyRoomsLiveView extends VersionedLiveView<MyRooms, List<RoomView>> {

    public static final String NAME = "my-rooms";

    private final RoomViewService rooms;

    public MyRoomsLiveView(RoomViewService rooms) {
        super(NAME, MyRooms.class);
        this.rooms = rooms;
    }

    @Override
    public boolean canSubscribe(String userId, String key) {
        return key.equals(userId);
    }

    @Override
    public Object query(String key) {
        return new GetMyRooms(key);
    }

    @Override
    public long version(MyRooms mine) {
        return mine.version();
    }

    @Override
    protected CompletableFuture<List<RoomView>> payload(String key, MyRooms mine) {
        return rooms.views(mine.rooms(), key);
    }
}
