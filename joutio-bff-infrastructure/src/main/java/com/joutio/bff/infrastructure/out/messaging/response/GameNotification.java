package com.joutio.bff.infrastructure.out.messaging.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.joutio.game.domain.model.GameQuestionChoice;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Notifications de partie — contrat web du BFF (annotations Jackson ; {@code type} discriminant).
 * Le payload du bus est un {@code GameEvent} typé ; le BFF le mappe vers ces DTOs.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = GameNotification.GameCreatedNotification.class, name = "GAME_CREATED"),
        @JsonSubTypes.Type(value = GameNotification.GameStartedNotification.class, name = "GAME_STARTED"),
        @JsonSubTypes.Type(value = GameNotification.RoundStartedNotification.class, name = "ROUND_STARTED"),
        @JsonSubTypes.Type(value = GameNotification.QuestionRevealedNotification.class, name = "QUESTION_REVEALED"),
        @JsonSubTypes.Type(value = GameNotification.PlayerAnsweredNotification.class, name = "PLAYER_ANSWERED"),
        @JsonSubTypes.Type(value = GameNotification.RoundClosedNotification.class, name = "ROUND_CLOSED"),
        @JsonSubTypes.Type(value = GameNotification.GameForfeitedNotification.class, name = "GAME_FORFEITED"),
        @JsonSubTypes.Type(value = GameNotification.GameEndedNotification.class, name = "GAME_ENDED"),
        @JsonSubTypes.Type(value = GameNotification.GameCancelledNotification.class, name = "GAME_CANCELLED")
})
public interface GameNotification {

    @JsonProperty("type")
    GameNotificationType type();

    String gameId();

    enum GameNotificationType {
        GAME_CREATED,
        GAME_STARTED,
        ROUND_STARTED,
        QUESTION_REVEALED,
        PLAYER_ANSWERED,
        ROUND_CLOSED,
        GAME_FORFEITED,
        GAME_ENDED,
        GAME_CANCELLED
    }

    /**
     * Les URLs d'images des questions de la partie (une par question illustrée, dans l'ordre des
     * rounds) : le client les précharge dès la création pour ne pas être pénalisé sur une
     * connexion faible. Questions sans image exclues.
     */
    record GameCreatedNotification(
            String gameId,
            String topicId,
            String player1Id,
            String player1Name,
            String player2Id,
            String player2Name,
            String player2Type,
            String botDifficulty,
            List<String> questionImageUrls
    ) implements GameNotification {
        @Override
        public GameNotificationType type() {
            return GameNotificationType.GAME_CREATED;
        }
    }

    record GameStartedNotification(
            String gameId,
            Instant firstRoundAt
    ) implements GameNotification {
        @Override
        public GameNotificationType type() {
            return GameNotificationType.GAME_STARTED;
        }
    }

    /** Contenu d'une question dans une langue (clé = code ISO 639-1 : fr, en). */
    record RoundQuestionContent(
            String text,
            Map<String, String> answers
    ) {
    }

    record RoundStartedNotification(
            String gameId,
            String round,
            String questionId,
            String questionText,
            String imageUrl,
            String difficulty,
            Map<String, String> answers,
            Map<String, RoundQuestionContent> translations,
            boolean bonus,
            Instant shownAt,
            Instant revealAt
    ) implements GameNotification {
        @Override
        public GameNotificationType type() {
            return GameNotificationType.ROUND_STARTED;
        }
    }

    record QuestionRevealedNotification(
            String gameId,
            String round,
            Instant revealedAt,
            Instant answerDeadlineAt
    ) implements GameNotification {
        @Override
        public GameNotificationType type() {
            return GameNotificationType.QUESTION_REVEALED;
        }
    }

    record PlayerAnsweredNotification(
            String gameId,
            String round,
            String playerId,
            GameQuestionChoice choice,
            boolean correct,
            int pointsEarned,
            Instant answeredAt,
            long timeMs
    ) implements GameNotification {
        @Override
        public GameNotificationType type() {
            return GameNotificationType.PLAYER_ANSWERED;
        }
    }

    record RoundClosedNotification(
            String gameId,
            String closedRound,
            String nextRound,
            GameQuestionChoice correctAnswer,
            Instant closedAt,
            Instant nextRoundAt
    ) implements GameNotification {
        @Override
        public GameNotificationType type() {
            return GameNotificationType.ROUND_CLOSED;
        }
    }

    record GameForfeitedNotification(
            String gameId,
            String forfeiterId
    ) implements GameNotification {
        @Override
        public GameNotificationType type() {
            return GameNotificationType.GAME_FORFEITED;
        }
    }

    record GameEndedNotification(
            String gameId,
            String winnerId,
            int player1FinalScore,
            int player2FinalScore
    ) implements GameNotification {
        @Override
        public GameNotificationType type() {
            return GameNotificationType.GAME_ENDED;
        }
    }

    record GameCancelledNotification(
            String gameId,
            String reason
    ) implements GameNotification {
        @Override
        public GameNotificationType type() {
            return GameNotificationType.GAME_CANCELLED;
        }
    }
}
