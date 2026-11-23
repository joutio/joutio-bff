package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.response.GameResultView;
import com.joutio.bff.infrastructure.in.api.response.ProgressionView;
import com.joutio.game.domain.command.GameCommand;
import com.joutio.game.domain.event.GameEvent;
import com.joutio.game.domain.exception.GameExceptions;
import com.joutio.game.domain.model.Game;
import com.joutio.game.domain.model.GamePlayerType;
import com.joutio.game.domain.model.GameQuestionChoice;
import com.joutio.game.domain.model.GameResult;
import com.joutio.game.domain.model.GameRoundType;
import com.joutio.game.domain.query.GameQuery;
import com.joutio.microservice.core.domain.model.event.EventEnvelope;
import com.joutio.profile.domain.model.Badge;
import com.joutio.profile.domain.model.GameXp;
import com.joutio.profile.domain.model.PlayerProgress;
import com.joutio.profile.domain.model.TopicProgress;
import com.joutio.profile.domain.query.ProgressionQuery;
import com.joutio.theme.domain.model.Topic;
import com.joutio.theme.domain.query.TopicQuery;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Composition du résultat de duel : récompense XP du duel et progression <b>à l'instant de la
 * partie</b> (snapshot porté par l'agrégat game + XP gagnée sur ce duel).
 */
class GameViewServiceTest {

    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private final QueryGateway queryGateway = mock(QueryGateway.class);
    private final ProfileLookup profileLookup = mock(ProfileLookup.class);
    private final ProgressionLookup progressionLookup = mock(ProgressionLookup.class);
    private final IdentityLookup identityLookup = mock(IdentityLookup.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final GameViewService service =
            new GameViewService(commandGateway, queryGateway, profileLookup, progressionLookup, identityLookup, meterRegistry);

    @Test
    void result_derives_victory_bonus_from_game_xp() {
        stubResult(gameResult(120, 90), List.of(new GameXp("game-1", 150, 18, true)));

        GameResultView view = service.result("game-1", "player-1").join();

        assertThat(view.myScore()).isEqualTo(120);
        assertThat(view.opponentScore()).isEqualTo(90);
        assertThat(view.reward()).isEqualTo(new GameResultView.RewardView(150, 30));
        assertThat(view.opponentRating()).isEqualTo(918);
        assertThat(view.topicMastery().xp()).isEqualTo(250);
        assertThat(view.topicMastery().xpGained()).isEqualTo(150);
    }

    @Test
    void answer_refused_after_the_deadline_is_counted() {
        when(commandGateway.send(any(GameCommand.AnswerQuestionCommand.class))).thenAnswer(invocation -> {
            GameCommand.AnswerQuestionCommand command = invocation.getArgument(0);
            return "game-late".equals(command.gameId())
                    ? CompletableFuture.failedFuture(new CommandExecutionException(
                            "late", null, Map.of("type", "urn:joutio:game:answerAfterDeadline")))
                    : CompletableFuture.completedFuture(null);
        });

        assertThatThrownBy(() -> service.answer("game-late", "player-1", GameRoundType.ROUND_1, GameQuestionChoice.A, 9_000L).join())
                .isInstanceOf(CompletionException.class);
        service.answer("game-1", "player-1", GameRoundType.ROUND_1, GameQuestionChoice.A, 2_000L).join();

        assertThat(meterRegistry.counter("joutio.duel.answers.late").count()).isEqualTo(1.0);
    }

    @Test
    void result_lists_the_badges_of_this_game_in_unlock_order() {
        stubResult(gameResult(120, 90), List.of(new GameXp("game-1", 150, 18, true)));

        GameResultView view = service.result("game-1", "player-1").join();

        assertThat(view.badges()).extracting(ProgressionView.BadgeView::code)
                .containsExactly("FIRST_WIN", "PERFECT");
    }

    @Test
    void result_builds_progression_from_game_snapshot_plus_gained_xp() {
        stubResult(gameResult(120, 90), List.of(new GameXp("game-1", 150, 18, true)));

        GameResultView view = service.result("game-1", "player-1").join();

        // Elo relatif à la partie : snapshot 800 + delta 18, pas l'Elo courant.
        assertThat(view.progression().rating()).isEqualTo(818);
        assertThat(view.opponentRating()).isEqualTo(918);
        // Maîtrise du sujet : XP courante 250, dont 150 gagnée sur ce duel.
        assertThat(view.topicMastery().xp()).isEqualTo(250);
        assertThat(view.topicMastery().xpGained()).isEqualTo(150);
    }

    @Test
    void result_has_no_reward_when_game_xp_is_missing() {
        stubResult(gameResult(120, 90), List.of());

        GameResultView view = service.result("game-1", "player-1").join();

        assertThat(view.reward()).isNull();
        assertThat(view.topicMastery().xp()).isEqualTo(250);
        assertThat(view.topicMastery().xpGained()).isZero();
    }

    @Test
    void result_training_bot_game_has_zero_reward_without_negative_victory_bonus() {
        stubResult(gameResult(120, 90, true), List.of(new GameXp("game-1", 0, 0, false)));

        GameResultView view = service.result("game-1", "player-1").join();

        assertThat(view.botGame()).isTrue();
        assertThat(view.reward()).isEqualTo(new GameResultView.RewardView(0, 0));
    }

    @Test
    void result_rejects_a_non_participant() {
        GameResult result = gameResult(120, 90);
        when(queryGateway.query(
                any(GameQuery.GetGameByIdQuery.class),
                ArgumentMatchers.<ResponseType<Game>>any()))
                .thenReturn(CompletableFuture.completedFuture(game(result)));

        assertThatThrownBy(() -> service.result("game-1", "intruder").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(BffProblems.GameAccessDeniedProblem.class);
    }

    @Test
    void events_are_served_from_the_event_store_without_waiting_for_the_projection() {
        EventEnvelope created = gameCreatedEnvelope();
        stubEvents(List.of(created));

        List<EventEnvelope> events = service.events("game-1", "player-1").join();

        assertThat(events).containsExactly(created);
        // La projection game (asynchrone) ne doit plus conditionner le bootstrap de l'arène.
        verify(queryGateway, never()).query(
                any(GameQuery.GetGameByIdQuery.class),
                ArgumentMatchers.<ResponseType<Game>>any());
    }

    @Test
    void events_are_reserved_to_participants() {
        stubEvents(List.of(gameCreatedEnvelope()));

        assertThatThrownBy(() -> service.events("game-1", "intruder").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(BffProblems.GameAccessDeniedProblem.class);
    }

    @Test
    void events_return_not_found_when_the_game_has_no_events() {
        stubEvents(List.of());

        assertThatThrownBy(() -> service.events("game-1", "player-1").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(GameExceptions.GameNotFoundProblem.class);
    }

    private void stubEvents(List<EventEnvelope> envelopes) {
        when(queryGateway.query(
                any(GameQuery.GetGameEventsQuery.class),
                ArgumentMatchers.<ResponseType<List<EventEnvelope>>>any()))
                .thenReturn(CompletableFuture.completedFuture(envelopes));
    }

    private static EventEnvelope gameCreatedEnvelope() {
        Instant at = Instant.parse("2026-06-01T10:00:00Z");
        return EventEnvelope.of(
                "game-1",
                0,
                at,
                new GameEvent.GameCreatedEvent(
                        "game-1", "topic-1",
                        "player-1", "Alice",
                        "player-2", "Bob",
                        GamePlayerType.HUMAN,
                        List.of(),
                        null, null, null,
                        at));
    }

    private void stubResult(GameResult result, List<GameXp> xp) {
        when(queryGateway.query(
                any(GameQuery.GetGameByIdQuery.class),
                ArgumentMatchers.<ResponseType<Game>>any()))
                .thenReturn(CompletableFuture.completedFuture(game(result)));
        when(queryGateway.query(
                any(GameQuery.GetGameResultQuery.class),
                ArgumentMatchers.<ResponseType<GameResult>>any()))
                .thenReturn(CompletableFuture.completedFuture(result));
        when(queryGateway.query(
                any(ProgressionQuery.GetGamesXpQuery.class),
                ArgumentMatchers.<ResponseType<List<GameXp>>>any()))
                .thenReturn(CompletableFuture.completedFuture(xp));
        when(queryGateway.query(
                any(TopicQuery.GetTopicsByIdsQuery.class),
                ArgumentMatchers.<ResponseType<List<Topic>>>any()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(profileLookup.get(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(progressionLookup.get(any())).thenAnswer(invocation ->
                CompletableFuture.completedFuture(PlayerProgress.empty(invocation.getArgument(0))));
        when(progressionLookup.getTopic(any(), any())).thenReturn(
                CompletableFuture.completedFuture(TopicProgress.of("topic-1", 250, 1)));
        Instant earned = Instant.parse("2026-11-19T10:00:00Z");
        when(progressionLookup.getUnlocks(any())).thenAnswer(invocation -> CompletableFuture.completedFuture(
                new com.joutio.profile.domain.model.PlayerUnlocks(invocation.getArgument(0), List.of(), List.of(
                        new com.joutio.profile.domain.model.BadgeUnlock(
                                invocation.getArgument(0), Badge.PERFECT, earned.plusSeconds(1), "game-1"),
                        new com.joutio.profile.domain.model.BadgeUnlock(
                                invocation.getArgument(0), Badge.FIRST_WIN, earned, "game-1"),
                        new com.joutio.profile.domain.model.BadgeUnlock(
                                invocation.getArgument(0), Badge.LIGHTNING, earned, "game-0"),
                        new com.joutio.profile.domain.model.BadgeUnlock(
                                invocation.getArgument(0), Badge.SOCIAL_BRONZE, earned, null)))));
        when(identityLookup.status(any())).thenReturn(CompletableFuture.completedFuture(
                new com.joutio.identity.domain.model.UserAccountStatus(
                        "player-2", com.joutio.identity.domain.model.AccountStatus.ACTIVE, null)));
    }

    private static Game game(GameResult result) {
        return Game.builder()
                .gameId(result.gameId())
                .topicId(result.topicId())
                .player1Id(result.player1Id())
                .player2Id(result.player2Id())
                .build();
    }

    private static GameResult gameResult(int myScore, int opponentScore) {
        return gameResult(myScore, opponentScore, false);
    }

    private static GameResult gameResult(int myScore, int opponentScore, boolean botGame) {
        return new GameResult(
                "game-1", "topic-1",
                "player-1", "Alice", "player-2", "Bob",
                "player-1", "player-2",
                myScore, opponentScore,
                myScore > opponentScore ? "player-1" : "player-2",
                botGame,
                null,
                100, 20, 6, 3, 7, 7,
                800, 900,
                List.of());
    }
}
