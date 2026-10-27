package com.joutio.bff.infrastructure.out.messaging.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;

/**
 * Notifications de salle — contrat web du BFF (diffusées sur {@code /topic/rooms/{roomId}}).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RoomNotification.RoomCreatedNotification.class, name = "ROOM_CREATED"),
        @JsonSubTypes.Type(value = RoomNotification.RoomEnteredNotification.class, name = "ROOM_ENTERED"),
        @JsonSubTypes.Type(value = RoomNotification.RoomAllPlayersPresentNotification.class, name = "ROOM_ALL_PRESENT"),
        @JsonSubTypes.Type(value = RoomNotification.RoomPlayersLoadedNotification.class, name = "ROOM_PLAYERS_LOADED"),
        @JsonSubTypes.Type(value = RoomNotification.RoomPlayedQuestionsNotification.class, name = "ROOM_PLAYED_QUESTIONS_LOADED"),
        @JsonSubTypes.Type(value = RoomNotification.RoomQuestionsLoadedNotification.class, name = "ROOM_QUESTIONS_LOADED"),
        @JsonSubTypes.Type(value = RoomNotification.RoomGameCreatedNotification.class, name = "ROOM_GAME_CREATED"),
        @JsonSubTypes.Type(value = RoomNotification.RoomLeftNotification.class, name = "ROOM_LEFT"),
        @JsonSubTypes.Type(value = RoomNotification.RoomWizzedNotification.class, name = "ROOM_WIZZED"),
        @JsonSubTypes.Type(value = RoomNotification.RoomCompletedNotification.class, name = "ROOM_COMPLETED"),
        @JsonSubTypes.Type(value = RoomNotification.RoomCancelledNotification.class, name = "ROOM_CANCELLED"),
        @JsonSubTypes.Type(value = RoomNotification.RoomExpiredNotification.class, name = "ROOM_EXPIRED"),
        @JsonSubTypes.Type(value = RoomNotification.RoomFailedNotification.class, name = "ROOM_FAILED")
})
public interface RoomNotification {

    @JsonProperty("type")
    RoomNotificationType type();

    String roomId();

    enum RoomNotificationType {
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

    record RoomCreatedNotification(
            String roomId,
            String topicId,
            String initiatorId,
            String opponentId,
            boolean vsBot,
            Instant expiresAt
    ) implements RoomNotification {
        @Override
        public RoomNotificationType type() {
            return RoomNotificationType.ROOM_CREATED;
        }
    }

    /** Un joueur apparaît dans la salle (présence, et enregistrement du second humain). */
    record RoomEnteredNotification(
            String roomId,
            String playerId
    ) implements RoomNotification {
        @Override
        public RoomNotificationType type() {
            return RoomNotificationType.ROOM_ENTERED;
        }
    }

    record RoomAllPlayersPresentNotification(
            String roomId
    ) implements RoomNotification {
        @Override
        public RoomNotificationType type() {
            return RoomNotificationType.ROOM_ALL_PRESENT;
        }
    }

    /** Profils des joueurs résolus (avancement de la préparation). */
    record RoomPlayersLoadedNotification(
            String roomId
    ) implements RoomNotification {
        @Override
        public RoomNotificationType type() {
            return RoomNotificationType.ROOM_PLAYERS_LOADED;
        }
    }

    /** Historique des questions jouées résolu (avancement de la préparation). */
    record RoomPlayedQuestionsNotification(
            String roomId
    ) implements RoomNotification {
        @Override
        public RoomNotificationType type() {
            return RoomNotificationType.ROOM_PLAYED_QUESTIONS_LOADED;
        }
    }

    /** Questions tirées : la saga lance la création de la partie. */
    record RoomQuestionsLoadedNotification(
            String roomId
    ) implements RoomNotification {
        @Override
        public RoomNotificationType type() {
            return RoomNotificationType.ROOM_QUESTIONS_LOADED;
        }
    }

    /** Le service game a accusé la création : le {@code gameId} est le deep link de l'arène. */
    record RoomGameCreatedNotification(
            String roomId,
            String gameId
    ) implements RoomNotification {
        @Override
        public RoomNotificationType type() {
            return RoomNotificationType.ROOM_GAME_CREATED;
        }
    }

    record RoomLeftNotification(
            String roomId,
            String playerId
    ) implements RoomNotification {
        @Override
        public RoomNotificationType type() {
            return RoomNotificationType.ROOM_LEFT;
        }
    }

    record RoomCompletedNotification(
            String roomId,
            String gameId
    ) implements RoomNotification {
        @Override
        public RoomNotificationType type() {
            return RoomNotificationType.ROOM_COMPLETED;
        }
    }

    /**
     * Relance (« wizz ») d'un adversaire absent : le client affiche la relance et cale la cadence
     * (anti-spam) du joueur émetteur sur {@code wizzedAt}.
     */
    record RoomWizzedNotification(
            String roomId,
            String playerId,
            String targetId,
            Instant wizzedAt
    ) implements RoomNotification {
        @Override
        public RoomNotificationType type() {
            return RoomNotificationType.ROOM_WIZZED;
        }
    }

    record RoomCancelledNotification(
            String roomId,
            String reason
    ) implements RoomNotification {
        @Override
        public RoomNotificationType type() {
            return RoomNotificationType.ROOM_CANCELLED;
        }
    }

    record RoomExpiredNotification(
            String roomId
    ) implements RoomNotification {
        @Override
        public RoomNotificationType type() {
            return RoomNotificationType.ROOM_EXPIRED;
        }
    }

    /** La partie n'a pas pu être préparée : les joueurs présents sont informés. */
    record RoomFailedNotification(
            String roomId,
            String reason
    ) implements RoomNotification {
        @Override
        public RoomNotificationType type() {
            return RoomNotificationType.ROOM_FAILED;
        }
    }
}
