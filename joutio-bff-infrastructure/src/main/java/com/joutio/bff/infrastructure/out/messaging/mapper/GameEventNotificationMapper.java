package com.joutio.bff.infrastructure.out.messaging.mapper;

import com.joutio.bff.infrastructure.out.messaging.response.GameNotification;
import com.joutio.game.domain.event.GameEvent;
import com.joutio.game.domain.model.GameQuestion;
import com.joutio.game.domain.model.GameQuestionChoice;
import com.joutio.game.domain.model.GameQuestionContent;
import com.joutio.microservice.core.domain.model.i18n.Language;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.isNull;

/**
 * Mappe les événements du domaine Game (reçus typés via le codec du bus ou via Kafka) vers le
 * contrat web {@link GameNotification}. Unique point de mapping pour l'historique REST et le WS.
 */
public final class GameEventNotificationMapper {

    private GameEventNotificationMapper() {
    }

    public static Optional<GameNotification> toNotification(GameEvent event) {
        if (isNull(event)) {
            return Optional.empty();
        }

        return switch (event) {
            case GameEvent.GameCreatedEvent gameCreatedEvent -> Optional.of(
                    new GameNotification.GameCreatedNotification(
                            gameCreatedEvent.gameId(),
                            gameCreatedEvent.topicId(),
                            gameCreatedEvent.player1Id(),
                            gameCreatedEvent.player1Name(),
                            gameCreatedEvent.player2Id(),
                            gameCreatedEvent.player2Name(),
                            gameCreatedEvent.player2Type() != null ? gameCreatedEvent.player2Type().name() : null,
                            gameCreatedEvent.botDifficulty() != null ? gameCreatedEvent.botDifficulty().name() : null,
                            gameCreatedEvent.questions().stream()
                                    .map(GameQuestion::imageUrl)
                                    .filter(Objects::nonNull)
                                    .toList()
                    )
            );

            case GameEvent.GameStartedEvent gameStartedEvent -> Optional.of(
                    new GameNotification.GameStartedNotification(
                            gameStartedEvent.gameId(),
                            gameStartedEvent.firstRoundAt()
                    )
            );

            case GameEvent.RoundStartedEvent roundStartedEvent -> {
                var question = roundStartedEvent.question();
                Map<String, String> answers = toAnswerMap(question.answers());
                Map<String, GameNotification.RoundQuestionContent> translations = new LinkedHashMap<>();
                if (question.translations() != null) {
                    for (Map.Entry<Language, GameQuestionContent> translation : question.translations().entrySet()) {
                        translations.put(translation.getKey().code(),
                                new GameNotification.RoundQuestionContent(
                                        translation.getValue().text(),
                                        toAnswerMap(translation.getValue().answers())));
                    }
                }
                yield Optional.of(
                        new GameNotification.RoundStartedNotification(
                                roundStartedEvent.gameId(),
                                roundStartedEvent.round().name(),
                                question.questionId(),
                                question.text(),
                                question.imageUrl(),
                                question.difficulty(),
                                answers,
                                translations,
                                roundStartedEvent.round().isBonus(),
                                roundStartedEvent.shownAt(),
                                roundStartedEvent.revealAt()
                        ));
            }

            case GameEvent.QuestionRevealedEvent questionRevealedEvent -> Optional.of(
                    new GameNotification.QuestionRevealedNotification(
                            questionRevealedEvent.gameId(),
                            questionRevealedEvent.round().name(),
                            questionRevealedEvent.revealedAt(),
                            questionRevealedEvent.answerDeadlineAt()
                    )
            );

            case GameEvent.QuestionAnsweredEvent questionAnsweredEvent -> Optional.of(
                    new GameNotification.PlayerAnsweredNotification(
                            questionAnsweredEvent.gameId(),
                            questionAnsweredEvent.round().name(),
                            questionAnsweredEvent.playerId(),
                            questionAnsweredEvent.choice(),
                            questionAnsweredEvent.correct(),
                            questionAnsweredEvent.pointsEarned(),
                            questionAnsweredEvent.answeredAt(),
                            questionAnsweredEvent.timeMs()
                    )
            );

            case GameEvent.RoundClosedEvent roundClosedEvent -> Optional.of(
                    new GameNotification.RoundClosedNotification(
                            roundClosedEvent.gameId(),
                            roundClosedEvent.closedRound().name(),
                            roundClosedEvent.nextRound() != null ? roundClosedEvent.nextRound().name() : null,
                            roundClosedEvent.correctAnswer(),
                            roundClosedEvent.closedAt(),
                            roundClosedEvent.nextRoundAt()
                    )
            );

            case GameEvent.GameEndedEvent gameEndedEvent -> Optional.of(
                    new GameNotification.GameEndedNotification(
                            gameEndedEvent.gameId(),
                            gameEndedEvent.winnerId(),
                            gameEndedEvent.player1FinalScore(),
                            gameEndedEvent.player2FinalScore()
                    )
            );

            case GameEvent.GameCancelledEvent gameCancelledEvent -> Optional.of(
                    new GameNotification.GameCancelledNotification(
                            gameCancelledEvent.gameId(),
                            gameCancelledEvent.reason()
                    )
            );

            case GameEvent.GameForfeitedEvent gameForfeitedEvent -> Optional.of(
                    new GameNotification.GameForfeitedNotification(
                            gameForfeitedEvent.gameId(),
                            gameForfeitedEvent.forfeiterId()
                    )
            );

            default -> Optional.empty();
        };
    }

    private static Map<String, String> toAnswerMap(Map<GameQuestionChoice, String> answers) {
        Map<String, String> mapped = new LinkedHashMap<>();
        if (answers != null) {
            answers.forEach((choice, text) -> mapped.put(choice.name(), text));
        }
        return mapped;
    }
}
