package com.joutio.bff.application.live;

import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.out.messaging.mapper.RoomEventResponseMapper;
import com.joutio.matchmaking.domain.query.RoomQuery.GetRoomEventsQuery;
import com.joutio.microservice.core.domain.model.event.EventEnvelope;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Flux d'une salle, {@code /topic/rooms/{roomId}} : historique puis chaque événement, émis par matchmaking au commit
 * ({@code LiveStreamHandler}). Contrat inchangé : {@code EventEnvelopeResponse}, dédoublonné par
 * {@code sequenceNumber}. Accès : tout joueur connecté (le lien de partage ouvre la salle au premier venu).
 */
@Component
public class RoomStreamLiveView extends AggregateStreamLiveView {

    public static final String DESTINATION_PREFIX = "/topic/rooms/";

    public RoomStreamLiveView() {
        super(DESTINATION_PREFIX);
    }

    @Override
    public boolean canSubscribe(String userId, String key) {
        return userId != null;
    }

    @Override
    public Object query(String key) {
        return new GetRoomEventsQuery(key);
    }

    @Override
    public CompletableFuture<EventEnvelopeResponse> message(String key, EventEnvelope event) {
        return CompletableFuture.completedFuture(RoomEventResponseMapper.toEnvelope(event).orElse(null));
    }
}
