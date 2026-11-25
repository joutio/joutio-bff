package com.joutio.bff.application.live;

import com.joutio.microservice.core.domain.model.event.EventEnvelope;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import org.axonframework.messaging.responsetypes.ResponseType;

import java.util.List;

/**
 * Vue vivante du flux d'un agrégat (duel, salle, ticket) : l'historique {@link EventEnvelope} en résultat initial,
 * puis chaque événement, versionné par son {@code sequenceNumber}. Le message garde le contrat de la destination
 * ({@code EventEnvelopeResponse} sur {@code /topic/games/{id}}…).
 */
public abstract class AggregateStreamLiveView implements LiveView<List<EventEnvelope>, EventEnvelope> {

    private static final ResponseType<List<EventEnvelope>> HISTORY =
            QueryResponseTypes.multipleInstancesOf(EventEnvelope.class);
    private static final ResponseType<EventEnvelope> EVENT = QueryResponseTypes.instanceOf(EventEnvelope.class);

    private final String destinationPrefix;

    protected AggregateStreamLiveView(String destinationPrefix) {
        this.destinationPrefix = destinationPrefix;
    }

    @Override
    public final String destinationPrefix() {
        return destinationPrefix;
    }

    @Override
    public final ResponseType<List<EventEnvelope>> initialResponseType() {
        return HISTORY;
    }

    @Override
    public final ResponseType<EventEnvelope> updateResponseType() {
        return EVENT;
    }

    @Override
    public final List<EventEnvelope> updatesOf(List<EventEnvelope> history) {
        return history == null ? List.of() : history;
    }

    @Override
    public final long version(EventEnvelope event) {
        return event.sequenceNumber();
    }
}
