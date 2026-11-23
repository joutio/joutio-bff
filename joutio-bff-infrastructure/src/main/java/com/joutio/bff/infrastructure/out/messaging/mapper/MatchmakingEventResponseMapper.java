package com.joutio.bff.infrastructure.out.messaging.mapper;

import com.joutio.bff.infrastructure.out.messaging.response.MatchmakingEventResponse;
import com.joutio.matchmaking.domain.event.MatchmakingEvent;

import java.util.Optional;

import static java.util.Objects.isNull;

/**
 * Mappe les événements d'appariement public vers le contrat web {@link MatchmakingEventResponse}.
 */
public final class MatchmakingEventResponseMapper {

    private MatchmakingEventResponseMapper() {
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
