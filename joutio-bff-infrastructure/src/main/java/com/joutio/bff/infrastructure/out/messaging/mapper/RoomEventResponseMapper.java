package com.joutio.bff.infrastructure.out.messaging.mapper;

import static java.util.Objects.isNull;

import com.joutio.bff.infrastructure.out.messaging.response.RoomEventResponse;
import com.joutio.matchmaking.domain.event.RoomEvent;

import java.util.Optional;

/** Mappe les événements de salle vers le contrat web {@link RoomEventResponse}. */
public final class RoomEventResponseMapper {

    private RoomEventResponseMapper() {}

    public static Optional<RoomEventResponse> toResponse(RoomEvent event) {
        if (isNull(event)) {
            return Optional.empty();
        }

        return switch (event) {
            case RoomEvent.RoomCreatedEvent e ->
                    Optional.of(
                            new RoomEventResponse.RoomCreatedEventResponse(
                                    e.roomId(),
                                    e.topicId(),
                                    e.initiatorId(),
                                    e.opponentId(),
                                    e.vsBot(),
                                    e.origin(),
                                    e.status(),
                                    e.sourceGameId(),
                                    e.expiresAt()));
            case RoomEvent.RoomAcceptedEvent e ->
                    Optional.of(new RoomEventResponse.RoomAcceptedEventResponse(e.roomId(), e.expiresAt()));
            case RoomEvent.RoomDeclinedEvent e ->
                    Optional.of(new RoomEventResponse.RoomDeclinedEventResponse(e.roomId()));
            case RoomEvent.RoomEnteredEvent e ->
                    Optional.of(
                            new RoomEventResponse.RoomEnteredEventResponse(e.roomId(), e.playerId()));
            case RoomEvent.RoomAllPlayersPresentEvent e ->
                    Optional.of(new RoomEventResponse.RoomAllPlayersPresentEventResponse(e.roomId()));
            case RoomEvent.RoomPlayersLoadedEvent e ->
                    Optional.of(new RoomEventResponse.RoomPlayersLoadedEventResponse(e.roomId()));
            case RoomEvent.RoomPlayedQuestionsLoadedEvent e ->
                    Optional.of(new RoomEventResponse.RoomPlayedQuestionsEventResponse(e.roomId()));
            case RoomEvent.RoomQuestionsLoadedEvent e ->
                    Optional.of(new RoomEventResponse.RoomQuestionsLoadedEventResponse(e.roomId()));
            case RoomEvent.RoomTopicAvailabilityConfirmedEvent e ->
                    Optional.of(new RoomEventResponse.RoomQuestionsLoadedEventResponse(e.roomId()));
            case RoomEvent.RoomGameCreatedEvent e ->
                    Optional.of(
                            new RoomEventResponse.RoomGameCreatedEventResponse(
                                    e.roomId(), e.gameId()));
            case RoomEvent.RoomLeftEvent e ->
                    Optional.of(
                            new RoomEventResponse.RoomLeftEventResponse(e.roomId(), e.playerId()));
            case RoomEvent.RoomRemindedEvent e ->
                    Optional.of(
                            new RoomEventResponse.RoomRemindedEventResponse(
                                    e.roomId(), e.playerId(), e.targetId(), e.stage(), e.remindedAt(), e.remindableAt()));
            case RoomEvent.RoomCompletedEvent e ->
                    Optional.of(
                            new RoomEventResponse.RoomCompletedEventResponse(e.roomId(), e.gameId()));
            case RoomEvent.RoomCancelledEvent e ->
                    Optional.of(
                            new RoomEventResponse.RoomCancelledEventResponse(e.roomId(), e.reason().name()));
            case RoomEvent.RoomExpiredEvent e ->
                    Optional.of(new RoomEventResponse.RoomExpiredEventResponse(e.roomId()));
            case RoomEvent.RoomFailedEvent e ->
                    Optional.of(
                            new RoomEventResponse.RoomFailedEventResponse(e.roomId(), e.reason()));
            default -> Optional.empty();
        };
    }
}
