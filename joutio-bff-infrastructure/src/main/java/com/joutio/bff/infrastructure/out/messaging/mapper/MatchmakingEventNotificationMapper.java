package com.joutio.bff.infrastructure.out.messaging.mapper;

import com.joutio.bff.infrastructure.out.messaging.response.MatchmakingNotification;
import com.joutio.matchmaking.domain.event.MatchmakingEvent;

import java.util.Optional;

import static java.util.Objects.isNull;

/**
 * Mappe les événements d'appariement public vers le contrat web {@link MatchmakingNotification}.
 */
public final class MatchmakingEventNotificationMapper {

    private MatchmakingEventNotificationMapper() {
    }

    public static Optional<MatchmakingNotification> toNotification(MatchmakingEvent event) {
        if (isNull(event)) {
            return Optional.empty();
        }

        return switch (event) {
            case MatchmakingEvent.MatchmakingStartedEvent e -> Optional.of(
                    new MatchmakingNotification.SearchingNotification(
                            e.matchmakingId(), e.topicId(), e.deadlineAt()));
            case MatchmakingEvent.MatchmakingMatchedEvent e -> Optional.of(
                    new MatchmakingNotification.MatchedNotification(
                            e.matchmakingId(), e.roomId(), e.opponentId(), e.vsBot()));
            case MatchmakingEvent.MatchmakingCancelledEvent e -> Optional.of(
                    new MatchmakingNotification.CancelledNotification(e.matchmakingId()));
            case MatchmakingEvent.MatchmakingExpiredEvent e -> Optional.of(
                    new MatchmakingNotification.ExpiredNotification(e.matchmakingId()));
            case MatchmakingEvent.MatchmakingFailedEvent e -> Optional.of(
                    new MatchmakingNotification.FailedNotification(e.matchmakingId(), e.reason()));
            default -> Optional.empty();
        };
    }
}
