package com.joutio.bff.infrastructure.out.messaging.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;

/**
 * Événements de salle — contrat web du BFF (diffusées sur {@code /topic/rooms/{roomId}}).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RoomEventResponse.RoomCreatedEventResponse.class, name = "ROOM_CREATED"),
        @JsonSubTypes.Type(value = RoomEventResponse.RoomEnteredEventResponse.class, name = "ROOM_ENTERED"),
        @JsonSubTypes.Type(value = RoomEventResponse.RoomAllPlayersPresentEventResponse.class, name = "ROOM_ALL_PRESENT"),
        @JsonSubTypes.Type(value = RoomEventResponse.RoomPlayersLoadedEventResponse.class, name = "ROOM_PLAYERS_LOADED"),
        @JsonSubTypes.Type(value = RoomEventResponse.RoomPlayedQuestionsEventResponse.class, name = "ROOM_PLAYED_QUESTIONS_LOADED"),
        @JsonSubTypes.Type(value = RoomEventResponse.RoomQuestionsLoadedEventResponse.class, name = "ROOM_QUESTIONS_LOADED"),
        @JsonSubTypes.Type(value = RoomEventResponse.RoomGameCreatedEventResponse.class, name = "ROOM_GAME_CREATED"),
        @JsonSubTypes.Type(value = RoomEventResponse.RoomLeftEventResponse.class, name = "ROOM_LEFT"),
        @JsonSubTypes.Type(value = RoomEventResponse.RoomWizzedEventResponse.class, name = "ROOM_WIZZED"),
        @JsonSubTypes.Type(value = RoomEventResponse.RoomCompletedEventResponse.class, name = "ROOM_COMPLETED"),
        @JsonSubTypes.Type(value = RoomEventResponse.RoomCancelledEventResponse.class, name = "ROOM_CANCELLED"),
        @JsonSubTypes.Type(value = RoomEventResponse.RoomExpiredEventResponse.class, name = "ROOM_EXPIRED"),
        @JsonSubTypes.Type(value = RoomEventResponse.RoomFailedEventResponse.class, name = "ROOM_FAILED")
})
public interface RoomEventResponse {

    @JsonProperty("type")
    RoomEventResponseType type();

    String roomId();

    enum RoomEventResponseType {
        ROOM_CREATED,
        ROOM_ENTERED,
        ROOM_ALL_PRESENT,
        ROOM_PLAYERS_LOADED,
        ROOM_PLAYED_QUESTIONS_LOADED,
        ROOM_QUESTIONS_LOADED,
        ROOM_GAME_CREATED,
        ROOM_LEFT,
        ROOM_WIZZED,
        ROOM_COMPLETED,
        ROOM_CANCELLED,
        ROOM_EXPIRED,
        ROOM_FAILED
    }

    record RoomCreatedEventResponse(
            String roomId,
            String topicId,
            String initiatorId,
            String opponentId,
            boolean vsBot,
            Instant expiresAt
    ) implements RoomEventResponse {
        @Override
        public RoomEventResponseType type() {
            return RoomEventResponseType.ROOM_CREATED;
        }
    }

    /** Un joueur apparaît dans la salle (présence, et enregistrement du second humain). */
    record RoomEnteredEventResponse(
            String roomId,
            String playerId
    ) implements RoomEventResponse {
        @Override
        public RoomEventResponseType type() {
            return RoomEventResponseType.ROOM_ENTERED;
        }
    }

    record RoomAllPlayersPresentEventResponse(
            String roomId
    ) implements RoomEventResponse {
        @Override
        public RoomEventResponseType type() {
            return RoomEventResponseType.ROOM_ALL_PRESENT;
        }
    }

    /** Profils des joueurs résolus (avancement de la préparation). */
    record RoomPlayersLoadedEventResponse(
            String roomId
    ) implements RoomEventResponse {
        @Override
        public RoomEventResponseType type() {
            return RoomEventResponseType.ROOM_PLAYERS_LOADED;
        }
    }

    /** Historique des questions jouées résolu (avancement de la préparation). */
    record RoomPlayedQuestionsEventResponse(
            String roomId
    ) implements RoomEventResponse {
        @Override
        public RoomEventResponseType type() {
            return RoomEventResponseType.ROOM_PLAYED_QUESTIONS_LOADED;
        }
    }

    /** Questions tirées : la saga lance la création de la partie. */
    record RoomQuestionsLoadedEventResponse(
            String roomId
    ) implements RoomEventResponse {
        @Override
        public RoomEventResponseType type() {
            return RoomEventResponseType.ROOM_QUESTIONS_LOADED;
        }
    }

    /** Le service game a accusé la création : le {@code gameId} est le deep link de l'arène. */
    record RoomGameCreatedEventResponse(
            String roomId,
            String gameId
    ) implements RoomEventResponse {
        @Override
        public RoomEventResponseType type() {
            return RoomEventResponseType.ROOM_GAME_CREATED;
        }
    }

    record RoomLeftEventResponse(
            String roomId,
            String playerId
    ) implements RoomEventResponse {
        @Override
        public RoomEventResponseType type() {
            return RoomEventResponseType.ROOM_LEFT;
        }
    }

    record RoomCompletedEventResponse(
            String roomId,
            String gameId
    ) implements RoomEventResponse {
        @Override
        public RoomEventResponseType type() {
            return RoomEventResponseType.ROOM_COMPLETED;
        }
    }

    /**
     * Relance (« wizz ») d'un adversaire absent : le client affiche la relance et cale la cadence
     * (anti-spam) du joueur émetteur sur {@code wizzedAt}.
     */
    record RoomWizzedEventResponse(
            String roomId,
            String playerId,
            String targetId,
            Instant wizzedAt
    ) implements RoomEventResponse {
        @Override
        public RoomEventResponseType type() {
            return RoomEventResponseType.ROOM_WIZZED;
        }
    }

    record RoomCancelledEventResponse(
            String roomId,
            String reason
    ) implements RoomEventResponse {
        @Override
        public RoomEventResponseType type() {
            return RoomEventResponseType.ROOM_CANCELLED;
        }
    }

    record RoomExpiredEventResponse(
            String roomId
    ) implements RoomEventResponse {
        @Override
        public RoomEventResponseType type() {
            return RoomEventResponseType.ROOM_EXPIRED;
        }
    }

    /** La partie n'a pas pu être préparée : les joueurs présents sont informés. */
    record RoomFailedEventResponse(
            String roomId,
            String reason
    ) implements RoomEventResponse {
        @Override
        public RoomEventResponseType type() {
            return RoomEventResponseType.ROOM_FAILED;
        }
    }
}
