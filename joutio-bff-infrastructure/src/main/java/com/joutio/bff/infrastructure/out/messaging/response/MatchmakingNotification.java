package com.joutio.bff.infrastructure.out.messaging.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;

/**
 * Notifications d'appariement public — contrat web du BFF
 * (diffusées sur {@code /topic/matchmaking/tickets/{ticketId}}).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MatchmakingNotification.SearchingNotification.class, name = "SEARCHING"),
        @JsonSubTypes.Type(value = MatchmakingNotification.MatchedNotification.class, name = "MATCHED"),
        @JsonSubTypes.Type(value = MatchmakingNotification.CancelledNotification.class, name = "CANCELLED"),
        @JsonSubTypes.Type(value = MatchmakingNotification.ExpiredNotification.class, name = "EXPIRED"),
        @JsonSubTypes.Type(value = MatchmakingNotification.FailedNotification.class, name = "FAILED")
})
public interface MatchmakingNotification {

    @JsonProperty("type")
    MatchmakingNotificationType type();

    String ticketId();

    enum MatchmakingNotificationType {
        SEARCHING,
        MATCHED,
        CANCELLED,
        EXPIRED,
        FAILED
    }

    record SearchingNotification(
            String ticketId,
            String topicId,
            Instant deadlineAt
    ) implements MatchmakingNotification {
        @Override
        public MatchmakingNotificationType type() {
            return MatchmakingNotificationType.SEARCHING;
        }
    }

    record MatchedNotification(
            String ticketId,
            String roomId,
            String opponentId,
            boolean vsBot
    ) implements MatchmakingNotification {
        @Override
        public MatchmakingNotificationType type() {
            return MatchmakingNotificationType.MATCHED;
        }
    }

    record CancelledNotification(
            String ticketId
    ) implements MatchmakingNotification {
        @Override
        public MatchmakingNotificationType type() {
            return MatchmakingNotificationType.CANCELLED;
        }
    }

    /** Fenêtre de recherche écoulée sans adversaire : le client propose relance ou entraînement. */
    record ExpiredNotification(
            String ticketId
    ) implements MatchmakingNotification {
        @Override
        public MatchmakingNotificationType type() {
            return MatchmakingNotificationType.EXPIRED;
        }
    }

    record FailedNotification(
            String ticketId,
            String reason
    ) implements MatchmakingNotification {
        @Override
        public MatchmakingNotificationType type() {
            return MatchmakingNotificationType.FAILED;
        }
    }
}
