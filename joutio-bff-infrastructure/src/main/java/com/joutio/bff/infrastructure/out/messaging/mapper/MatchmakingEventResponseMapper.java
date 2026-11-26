package com.joutio.bff.infrastructure.out.messaging.mapper;

import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.out.messaging.response.MatchmakingEventResponse;
import com.joutio.matchmaking.domain.event.MatchmakingEvent;
import com.joutio.microservice.core.domain.model.event.EventEnvelope;

import java.util.Optional;

import static java.util.Objects.isNull;

/**
 * Mappe les événements d'appariement public vers le contrat web {@link MatchmakingEventResponse}.
 */
public final class MatchmakingEventResponseMapper {

    private MatchmakingEventResponseMapper() {
    }

    /** Événement de l'historique ou du flux vivant d'un ticket d'appariement, au contrat de {@code /topic/matchmaking/tickets/{id}}. */
    public static Optional<EventEnvelopeResponse> toEnvelope(EventEnvelope envelope) {
        if (!(envelope.payload() instanceof MatchmakingEvent event)) {
            return Optional.empty();
        }
        return toResponse(event).map(response -> EventEnvelopeResponse.of(
                envelope.aggregateId(),
                envelope.sequenceNumber(),
                envelope.timestamp(),
                response.type().name(),
                response));
    }

    public static Optional<MatchmakingEventResponse> toResponse(MatchmakingEvent event) {
        if (isNull(event)) {
            return Optional.empty();
        }

        return switch (event) {
            case MatchmakingEvent.MatchmakingStartedEvent e -> Optional.of(
                    new MatchmakingEventResponse.SearchingEventResponse(
                            e.matchmakingId(), e.topicId(), e.deadlineAt()));
            case MatchmakingEvent.MatchmakingMatchedEvent e -> Optional.of(
                    new MatchmakingEventResponse.MatchedEventResponse(
                            e.matchmakingId(), e.roomId(), e.opponentId(), e.vsBot(), null));
            case MatchmakingEvent.MatchmakingCancelledEvent e -> Optional.of(
                    new MatchmakingEventResponse.CancelledEventResponse(e.matchmakingId()));
            case MatchmakingEvent.MatchmakingExpiredEvent e -> Optional.of(
                    new MatchmakingEventResponse.ExpiredEventResponse(e.matchmakingId()));
            case MatchmakingEvent.MatchmakingFailedEvent e -> Optional.of(
                    new MatchmakingEventResponse.FailedEventResponse(e.matchmakingId(), e.reason()));
            default -> Optional.empty();
        };
    }
}
