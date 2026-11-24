package com.joutio.bff.application;

import com.joutio.bff.application.TestUserBlocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.request.CreateChallengeRequest;
import com.joutio.game.domain.model.Game;
import com.joutio.game.domain.model.GamePlayerType;
import com.joutio.game.domain.query.GameQuery;
import com.joutio.matchmaking.domain.command.RoomCommand;
import com.joutio.matchmaking.domain.model.RoomOrigin;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.concurrent.CompletableFuture;

/**
 * Création d'un défi ({@code POST /api/rooms/challenge}) : contrôles de l'adversaire, garde, et validation de la
 * revanche ({@code gameId} : participation du lanceur, duel entre humains, cohérence de l'adversaire). La salle naît
 * {@code INVITED} côté matchmaking.
 */
class RoomChallengeCreationTest {

    private static final String CHALLENGER = "challenger-1";
    private static final String OPPONENT = "opponent-1";

    private final QueryGateway queryGateway = mock(QueryGateway.class);
    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private final ProfileLookup profileLookup = mock(ProfileLookup.class);
    private final IdentityLookup identityLookup = mock(IdentityLookup.class);
    private final DuelCreationGuard duelCreationGuard = mock(DuelCreationGuard.class);
    private final RoomViewService service =
            new RoomViewService(
                    queryGateway,
                    commandGateway,
                    profileLookup,
                    mock(ProgressionLookup.class),
                    identityLookup,
                    duelCreationGuard,
                    TestUserBlocks.none());

    @BeforeEach
    void setUp() {
        when(profileLookup.get(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(identityLookup.status(any()))
                .thenReturn(
                        CompletableFuture.completedFuture(
                                new com.joutio.identity.domain.model.UserAccountStatus(
                                        OPPONENT,
                                        com.joutio.identity.domain.model.AccountStatus.ACTIVE,
                                        null)));
        when(duelCreationGuard.ensureChallengeAllowed(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void create_normalChallenge_skipsRematchValidation() {
        service.createChallenge(CHALLENGER, request(OPPONENT, null)).join();

        RoomCommand.CreateRoomCommand command = capturedCommand();
        assertThat(command.origin()).isEqualTo(RoomOrigin.CHALLENGE);
        assertThat(command.initiatorId()).isEqualTo(CHALLENGER);
        assertThat(command.opponentId()).isEqualTo(OPPONENT);
        assertThat(command.sourceGameId()).isNull();
        verify(queryGateway, never())
                .query(
                        any(GameQuery.GetGameByIdQuery.class),
                        ArgumentMatchers.<ResponseType<Game>>any());
    }

    @Test
    void create_refusedWhenOpponentDeleted() {
        when(identityLookup.status(OPPONENT))
                .thenReturn(
                        CompletableFuture.completedFuture(
                                new com.joutio.identity.domain.model.UserAccountStatus(
                                        OPPONENT,
                                        com.joutio.identity.domain.model.AccountStatus.DELETED,
                                        null)));

        assertThatThrownBy(() -> service.createChallenge(CHALLENGER, request(OPPONENT, "game-1")).join())
                .hasCauseInstanceOf(BffProblems.ChallengeOpponentDeletedProblem.class);
        verify(commandGateway, never()).send(any());
    }

    @Test
    void create_rematch_opensARematchRoomWithItsSourceGame() {
        stubGame(game(CHALLENGER, OPPONENT, GamePlayerType.HUMAN));

        service.createChallenge(CHALLENGER, request(OPPONENT, "game-1")).join();

        RoomCommand.CreateRoomCommand command = capturedCommand();
        assertThat(command.origin()).isEqualTo(RoomOrigin.REMATCH);
        assertThat(command.sourceGameId()).isEqualTo("game-1");
    }

    @Test
    void create_refusedBeforeCommandWhenDuelGuardRejects() {
        when(duelCreationGuard.ensureChallengeAllowed(CHALLENGER, OPPONENT, "topic-1"))
                .thenReturn(
                        CompletableFuture.failedFuture(
                                new BffProblems.ChallengeAlreadyPendingProblem(
                                        "topic-1", OPPONENT)));

        assertThatThrownBy(() -> service.createChallenge(CHALLENGER, request(OPPONENT, null)).join())
                .hasCauseInstanceOf(BffProblems.ChallengeAlreadyPendingProblem.class);
        verify(commandGateway, never()).send(any());
    }

    @Test
    void create_rematch_refusedWhenChallengerDidNotPlay() {
        stubGame(game("someone-else", OPPONENT, GamePlayerType.HUMAN));

        assertThatThrownBy(() -> service.createChallenge(CHALLENGER, request(OPPONENT, "game-1")).join())
                .hasCauseInstanceOf(BffProblems.RematchNotAllowedProblem.class);
    }

    @Test
    void create_rematch_refusedForBotGame() {
        stubGame(game(CHALLENGER, "bot-1", GamePlayerType.BOT));

        assertThatThrownBy(() -> service.createChallenge(CHALLENGER, request(OPPONENT, "game-1")).join())
                .hasCauseInstanceOf(BffProblems.InvalidRematchProblem.class);
    }

    @Test
    void create_rematch_refusedOnOpponentMismatch() {
        stubGame(game(CHALLENGER, OPPONENT, GamePlayerType.HUMAN));

        assertThatThrownBy(() -> service.createChallenge(CHALLENGER, request("other-1", "game-1")).join())
                .hasCauseInstanceOf(BffProblems.InvalidRematchProblem.class);
    }

    private void stubGame(Game game) {
        when(queryGateway.query(
                        any(GameQuery.GetGameByIdQuery.class),
                        ArgumentMatchers.<ResponseType<Game>>any()))
                .thenReturn(CompletableFuture.completedFuture(game));
    }

    private RoomCommand.CreateRoomCommand capturedCommand() {
        ArgumentCaptor<RoomCommand.CreateRoomCommand> captor =
                ArgumentCaptor.forClass(RoomCommand.CreateRoomCommand.class);
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

    @org.junit.jupiter.api.BeforeEach
    void publishedTopic() {
        org.mockito.Mockito.lenient()
                .when(
                        queryGateway.query(
                                org.mockito.ArgumentMatchers.any(
                                        com.joutio.theme.domain.query.TopicQuery.GetTopicByIdQuery
                                                .class),
                                org.mockito.ArgumentMatchers.any(
                                        org.axonframework.messaging.responsetypes.ResponseType
                                                .class)))
                .thenReturn(
                        java.util.concurrent.CompletableFuture.completedFuture(
                                com.joutio.theme.domain.model.Topic.builder()
                                        .topicId("topic-1")
                                        .status(com.joutio.theme.domain.model.TopicStatus.PUBLISHED)
                                        .build()));
    }
}