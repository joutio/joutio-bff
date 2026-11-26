package com.joutio.bff.application.live;

import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.out.messaging.mapper.GameEventResponseMapper;
import com.joutio.game.domain.query.GameQuery.GetGameEventsQuery;
import com.joutio.microservice.core.domain.model.event.EventEnvelope;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Flux d'une partie, {@code /topic/games/{gameId}} : historique puis chaque événement, émis par game dans l'unité de
 * travail de la commande ({@code GameLiveStreamHandler}). Contrat inchangé : {@code EventEnvelopeResponse}, dédoublonné
 * par {@code sequenceNumber}.
 *
 * <p>Accès : tout joueur connecté, comme avant la vue vivante. La participation se vérifie sur l'historique REST
 * ({@code GET /api/games/{id}/events}), pas ici : la règle d'abonnement reste locale, sans I/O.</p>
 */
@Component
public class GameStreamLiveView extends AggregateStreamLiveView {

    public static final String DESTINATION_PREFIX = "/topic/games/";

    public GameStreamLiveView() {
        super(DESTINATION_PREFIX);
    }

    @Override
    public boolean canSubscribe(String userId, String key) {
        return userId != null;
    }

    @Override
    public Object query(String key) {
        return new GetGameEventsQuery(key);
    }

    @Override
    public CompletableFuture<EventEnvelopeResponse> message(String key, EventEnvelope event) {
        return CompletableFuture.completedFuture(GameEventResponseMapper.toEnvelope(event).orElse(null));
    }
}
