package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.request.CreateChallengeRequest;
import com.joutio.game.domain.model.Game;
import com.joutio.game.domain.model.GamePlayerType;
import com.joutio.game.domain.query.GameQuery;
import com.joutio.matchmaking.domain.command.ChallengeCommand;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validation du défi de revanche : {@code gameId} référence la partie terminée d'origine —
 * participation du challenger, duel humain/humain et cohérence de l'adversaire.
 */
class ChallengeViewServiceTest {

    private static final String CHALLENGER = "challenger-1";
    private static final String OPPONENT = "opponent-1";

    private final QueryGateway queryGateway = mock(QueryGateway.class);
    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private final ProfileLookup profileLookup = mock(ProfileLookup.class);
    private final IdentityLookup identityLookup = mock(IdentityLookup.class);
    private final DuelCreationGuard duelCreationGuard = mock(DuelCreationGuard.class);
    private final ChallengeViewService service =
            new ChallengeViewService(queryGateway, commandGateway, profileLookup, identityLookup,
                    duelCreationGuard);

    @BeforeEach
    void setUp() {
        when(profileLookup.get(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(identityLookup.status(any())).thenReturn(CompletableFuture.completedFuture(
                new com.joutio.identity.domain.model.UserAccountStatus(
                        OPPONENT, com.joutio.identity.domain.model.AccountStatus.ACTIVE, null)));
        when(duelCreationGuard.ensureChallengeAllowed(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void create_normalChallenge_skipsRematchValidation() {
        service.create(CHALLENGER, request(OPPONENT, null)).join();

        ChallengeCommand.CreateChallengeCommand command = capturedCommand();
        assertThat(command.gameId()).isNull();
        verify(queryGateway, never()).query(
                any(GameQuery.GetGameByIdQuery.class), ArgumentMatchers.<ResponseType<Game>>any());
    }

    @Test
    void create_refusedWhenOpponentDeleted() {
        when(identityLookup.status(OPPONENT)).thenReturn(CompletableFuture.completedFuture(
                new com.joutio.identity.domain.model.UserAccountStatus(
                        OPPONENT,
                        com.joutio.identity.domain.model.AccountStatus.DELETED,
                        null)));

        assertThatThrownBy(() -> service.create(CHALLENGER, request(OPPONENT, "game-1")).join())
                .hasCauseInstanceOf(BffProblems.ChallengeOpponentDeletedProblem.class);
        verify(commandGateway, never()).send(any());
    }

    @Test
    void create_rematch_passesSourceGameIdToCommand() {
        stubGame(game(CHALLENGER, OPPONENT, GamePlayerType.HUMAN));

        service.create(CHALLENGER, request(OPPONENT, "game-1")).join();

        assertThat(capturedCommand().gameId()).isEqualTo("game-1");
    }

    @Test
    void create_refusedBeforeCommandWhenDuelGuardRejects() {
        when(duelCreationGuard.ensureChallengeAllowed(CHALLENGER, OPPONENT, "topic-1"))
                .thenReturn(CompletableFuture.failedFuture(
                        new BffProblems.ChallengeAlreadyPendingProblem("topic-1", OPPONENT)));

        assertThatThrownBy(() -> service.create(CHALLENGER, request(OPPONENT, null)).join())
                .hasCauseInstanceOf(BffProblems.ChallengeAlreadyPendingProblem.class);
        verify(commandGateway, never()).send(any());
    }

    @Test
    void create_rematch_refusedWhenChallengerDidNotPlay() {
        stubGame(game("someone-else", OPPONENT, GamePlayerType.HUMAN));

        assertThatThrownBy(() -> service.create(CHALLENGER, request(OPPONENT, "game-1")).join())
                .hasCauseInstanceOf(BffProblems.RematchNotAllowedProblem.class);
    }

    @Test
    void create_rematch_refusedForBotGame() {
        stubGame(game(CHALLENGER, "bot-1", GamePlayerType.BOT));

        assertThatThrownBy(() -> service.create(CHALLENGER, request(OPPONENT, "game-1")).join())
                .hasCauseInstanceOf(BffProblems.InvalidRematchProblem.class);
    }

    @Test
    void create_rematch_refusedOnOpponentMismatch() {
        stubGame(game(CHALLENGER, OPPONENT, GamePlayerType.HUMAN));

        assertThatThrownBy(() -> service.create(CHALLENGER, request("other-1", "game-1")).join())
                .hasCauseInstanceOf(BffProblems.InvalidRematchProblem.class);
    }

    private void stubGame(Game game) {
        when(queryGateway.query(
                any(GameQuery.GetGameByIdQuery.class),
                ArgumentMatchers.<ResponseType<Game>>any()))
                .thenReturn(CompletableFuture.completedFuture(game));
    }

    private ChallengeCommand.CreateChallengeCommand capturedCommand() {
        ArgumentCaptor<ChallengeCommand.CreateChallengeCommand> captor =
                ArgumentCaptor.forClass(ChallengeCommand.CreateChallengeCommand.class);
        verify(commandGateway).send(captor.capture());
        return captor.getValue();
    }

    private static CreateChallengeRequest request(String opponentId, String gameId) {
        return new CreateChallengeRequest("topic-1", opponentId, gameId);
    }

    private static Game game(String player1Id, String player2Id, GamePlayerType opponent) {
        return Game.builder()
                .gameId("game-1")
                .topicId("topic-1")
                .player1Id(player1Id)
                .player1Name("P1")
                .player2Id(player2Id)
                .player2Name("P2")
                .opponent(opponent)
                .build();
    }
}
