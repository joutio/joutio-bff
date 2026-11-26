package com.joutio.bff.application.live;

import com.joutio.bff.application.MatchmakingViewService;
import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.out.messaging.mapper.MatchmakingEventResponseMapper;
import com.joutio.matchmaking.domain.event.MatchmakingEvent;
import com.joutio.matchmaking.domain.query.MatchmakingQuery.GetMatchmakingEventsQuery;
import com.joutio.microservice.core.domain.model.event.EventEnvelope;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Flux d'un ticket d'appariement public, {@code /topic/matchmaking/tickets/{ticketId}} : historique puis chaque
 * événement, émis par matchmaking au commit ({@code LiveStreamHandler}). {@code MATCHED} est enrichi du profil de
 * l'adversaire ({@link MatchmakingViewService#withOpponent}). Accès : tout joueur connecté, l'identifiant du ticket
 * n'étant connu que de son joueur.
 */
@Component
public class TicketStreamLiveView extends AggregateStreamLiveView {

    public static final String DESTINATION_PREFIX = "/topic/matchmaking/tickets/";

    private final MatchmakingViewService matchmaking;

    public TicketStreamLiveView(MatchmakingViewService matchmaking) {
        super(DESTINATION_PREFIX);
        this.matchmaking = matchmaking;
    }

    @Override
    public boolean canSubscribe(String userId, String key) {
        return userId != null;
    }

    @Override
    public Object query(String key) {
        return new GetMatchmakingEventsQuery(key);
    }

    @Override
    public CompletableFuture<EventEnvelopeResponse> message(String key, EventEnvelope event) {
        if (!(event.payload() instanceof MatchmakingEvent domainEvent)) {
            return CompletableFuture.completedFuture(null);
        }
        return MatchmakingEventResponseMapper.toResponse(domainEvent)
                .map(response -> matchmaking.withOpponent(response).thenApply(enriched -> EventEnvelopeResponse.of(
                        event.aggregateId(),
                        event.sequenceNumber(),
                        event.timestamp(),
                        enriched.type().name(),
                        enriched)))
                .orElse(CompletableFuture.completedFuture(null));
    }
}
