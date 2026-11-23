package com.joutio.bff.infrastructure.out.messaging.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.joutio.bff.infrastructure.in.api.response.RoomOpponentView;

import java.time.Instant;

/**
 * Événements d'appariement public — contrat web du BFF
 * (diffusées sur {@code /topic/matchmaking/tickets/{ticketId}}).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MatchmakingEventResponse.SearchingEventResponse.class, name = "SEARCHING"),
        @JsonSubTypes.Type(value = MatchmakingEventResponse.MatchedEventResponse.class, name = "MATCHED"),
        @JsonSubTypes.Type(value = MatchmakingEventResponse.CancelledEventResponse.class, name = "CANCELLED"),
        @JsonSubTypes.Type(value = MatchmakingEventResponse.ExpiredEventResponse.class, name = "EXPIRED"),
        @JsonSubTypes.Type(value = MatchmakingEventResponse.FailedEventResponse.class, name = "FAILED")
})
public interface MatchmakingEventResponse {

    @JsonProperty("type")
    MatchmakingEventResponseType type();

    String ticketId();

    enum MatchmakingEventResponseType {
        SEARCHING,
        MATCHED,
        CANCELLED,
        EXPIRED,
        FAILED
    }

    record SearchingEventResponse(
            String ticketId,
            String topicId,
            Instant deadlineAt
    ) implements MatchmakingEventResponse {
        @Override
        public MatchmakingEventResponseType type() {
            return MatchmakingEventResponseType.SEARCHING;
        }
    }

    record MatchedEventResponse(
            String ticketId,
            String roomId,
            String opponentId,
            boolean vsBot,
            /**
             * Adversaire enrichi (pseudo, avatar, Elo, pays) : la carte « Adversaire trouvé » n'attend pas la
             * lecture de la salle. {@code null} si le profil est momentanément indisponible.
             */
            RoomOpponentView opponent
    ) implements MatchmakingEventResponse {
        @Override
        public MatchmakingEventResponseType type() {
            return MatchmakingEventResponseType.MATCHED;
        }
    }

    record CancelledEventResponse(
            String ticketId
    ) implements MatchmakingEventResponse {
        @Override
        public MatchmakingEventResponseType type() {
            return MatchmakingEventResponseType.CANCELLED;
        }
    }

    /** Fenêtre de recherche écoulée sans adversaire : le client propose relance ou entraînement. */
    record ExpiredEventResponse(
            String ticketId
    ) implements MatchmakingEventResponse {
        @Override
        public MatchmakingEventResponseType type() {
            return MatchmakingEventResponseType.EXPIRED;
        }
    }

    record FailedEventResponse(
            String ticketId,
            String reason
    ) implements MatchmakingEventResponse {
        @Override
        public MatchmakingEventResponseType type() {
            return MatchmakingEventResponseType.FAILED;
        }
    }
}
