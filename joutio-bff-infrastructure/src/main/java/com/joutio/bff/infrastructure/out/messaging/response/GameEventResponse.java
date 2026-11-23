package com.joutio.bff.infrastructure.out.messaging.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.joutio.game.domain.model.GameQuestionChoice;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Événements de partie — contrat web du BFF (annotations Jackson ; {@code type} discriminant).
 * Le payload du bus est un {@code GameEvent} typé ; le BFF le mappe vers ces DTOs.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = GameEventResponse.GameCreatedEventResponse.class, name = "GAME_CREATED"),
        @JsonSubTypes.Type(value = GameEventResponse.GameStartedEventResponse.class, name = "GAME_STARTED"),
        @JsonSubTypes.Type(value = GameEventResponse.RoundStartedEventResponse.class, name = "ROUND_STARTED"),
        @JsonSubTypes.Type(value = GameEventResponse.QuestionRevealedEventResponse.class, name = "QUESTION_REVEALED"),
        @JsonSubTypes.Type(value = GameEventResponse.PlayerAnsweredEventResponse.class, name = "PLAYER_ANSWERED"),
        @JsonSubTypes.Type(value = GameEventResponse.RoundClosedEventResponse.class, name = "ROUND_CLOSED"),
        @JsonSubTypes.Type(value = GameEventResponse.GameForfeitedEventResponse.class, name = "GAME_FORFEITED"),
        @JsonSubTypes.Type(value = GameEventResponse.GameEndedEventResponse.class, name = "GAME_ENDED"),
        @JsonSubTypes.Type(value = GameEventResponse.GameCancelledEventResponse.class, name = "GAME_CANCELLED")
})
public interface GameEventResponse {

    @JsonProperty("type")
    GameEventResponseType type();

    String gameId();

    enum GameEventResponseType {
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
    record GameCreatedEventResponse(
            String gameId,
            String topicId,
            String player1Id,
            String player1Name,
            String player2Id,
            String player2Name,
            String player2Type,
            String botDifficulty,
            List<String> questionImageUrls
    ) implements GameEventResponse {
        @Override
        public GameEventResponseType type() {
            return GameEventResponseType.GAME_CREATED;
        }
    }

    record GameStartedEventResponse(
            String gameId,
            Instant firstRoundAt
    ) implements GameEventResponse {
        @Override
        public GameEventResponseType type() {
            return GameEventResponseType.GAME_STARTED;
        }
    }

    /** Contenu d'une question dans une langue (clé = code ISO 639-1 : fr, en). */
    /** Contenu d'une langue ; {@code imageAlt} : description de l'image, {@code null} si absente. */
    record RoundQuestionContent(
            String text,
            Map<String, String> answers,
            String imageAlt
    ) {
    }

    record RoundStartedEventResponse(
            String gameId,
            String round,
            String questionId,
            String questionText,
            String imageUrl,
            /** Description de l'image (repli FR, comme {@code questionText}) ; {@code null} si absente. */
            String imageAlt,
            String difficulty,
            Map<String, String> answers,
            Map<String, RoundQuestionContent> translations,
            boolean bonus,
            Instant shownAt,
            Instant revealAt
    ) implements GameEventResponse {
        @Override
        public GameEventResponseType type() {
            return GameEventResponseType.ROUND_STARTED;
        }
    }

    record QuestionRevealedEventResponse(
            String gameId,
            String round,
            Instant revealedAt,
            Instant answerDeadlineAt
    ) implements GameEventResponse {
        @Override
        public GameEventResponseType type() {
            return GameEventResponseType.QUESTION_REVEALED;
        }
    }

    record PlayerAnsweredEventResponse(
            String gameId,
            String round,
            String playerId,
            GameQuestionChoice choice,
            boolean correct,
            int pointsEarned,
            Instant answeredAt,
            long timeMs
    ) implements GameEventResponse {
        @Override
        public GameEventResponseType type() {
            return GameEventResponseType.PLAYER_ANSWERED;
        }
    }

    record RoundClosedEventResponse(
            String gameId,
            String closedRound,
            String nextRound,
            GameQuestionChoice correctAnswer,
            Instant closedAt,
            Instant nextRoundAt
    ) implements GameEventResponse {
        @Override
        public GameEventResponseType type() {
            return GameEventResponseType.ROUND_CLOSED;
        }
    }

    record GameForfeitedEventResponse(
            String gameId,
            String forfeiterId
    ) implements GameEventResponse {
        @Override
        public GameEventResponseType type() {
            return GameEventResponseType.GAME_FORFEITED;
        }
    }

    record GameEndedEventResponse(
            String gameId,
            String winnerId,
            int player1FinalScore,
            int player2FinalScore
    ) implements GameEventResponse {
        @Override
        public GameEventResponseType type() {
            return GameEventResponseType.GAME_ENDED;
        }
    }

    record GameCancelledEventResponse(
            String gameId,
            String reason
    ) implements GameEventResponse {
        @Override
        public GameEventResponseType type() {
            return GameEventResponseType.GAME_CANCELLED;
        }
    }
}
