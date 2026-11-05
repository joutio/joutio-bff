package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.request.CreateBotRoomRequest;
import com.joutio.bff.infrastructure.in.api.request.CreateRoomRequest;
import com.joutio.bff.infrastructure.in.api.response.RoomView;
import com.joutio.matchmaking.domain.command.RoomCommand;
import com.joutio.matchmaking.domain.model.Room;
import com.joutio.matchmaking.domain.model.RoomStatus;
import com.joutio.matchmaking.domain.query.RoomQuery;
import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.profile.domain.model.PlayerProgress;
import com.joutio.profile.domain.model.Profile;
import com.joutio.profile.domain.model.TopicProgress;
import com.joutio.profile.domain.query.ProgressionQuery;
import com.joutio.theme.domain.query.TopicQuery;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Vérifie que la façade prépare le snapshot de profil dans la commande d'apparition (aucune I/O
 * de résolution des joueurs côté saga salle).
 */
class RoomViewServiceTest {

    private final QueryGateway queryGateway = mock(QueryGateway.class);
    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private final ProfileLookup profileLookup = mock(ProfileLookup.class);
    private final ProgressionLookup progressionLookup = mock(ProgressionLookup.class);
    private final IdentityLookup identityLookup = mock(IdentityLookup.class);
    private final DuelCreationGuard duelCreationGuard = mock(DuelCreationGuard.class);

    private final RoomViewService service =
            new RoomViewService(queryGateway, commandGateway, profileLookup, progressionLookup,
                    identityLookup, duelCreationGuard);

    @Test
    void create_refusedBeforeCommandWhenDuelGuardRejects() {
        when(duelCreationGuard.ensureRoomAllowed("user-1", "topic-1"))
                .thenReturn(CompletableFuture.failedFuture(
                        new BffProblems.RoomAlreadyOpenOnTopicProblem("topic-1", "room-1")));

        assertThatThrownBy(() ->
                service.create("user-1", new CreateRoomRequest("topic-1")).join())
                .hasCauseInstanceOf(BffProblems.RoomAlreadyOpenOnTopicProblem.class);
        verify(commandGateway, never()).send(any());
    }

    @Test
    void createBot_usesBotQuotaWithoutHumanRoomUniqueness() {
        when(duelCreationGuard.ensureBotRoomAllowed("user-1"))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(commandGateway.send(any(RoomCommand.CreateRoomCommand.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        service.createBot("user-1", new CreateBotRoomRequest("topic-1", null)).join();

        verify(duelCreationGuard).ensureBotRoomAllowed("user-1");
        verify(duelCreationGuard, never()).ensureRoomAllowed(any(), any());
        verify(commandGateway).send(any(RoomCommand.CreateRoomCommand.class));
    }

    @Test
    void join_preparesPlayerSnapshotInCommand() {
        stubRoom();
        when(profileLookup.get("user-1")).thenReturn(CompletableFuture.completedFuture(profile()));
        when(progressionLookup.get("user-1")).thenReturn(CompletableFuture.completedFuture(progress()));
        when(progressionLookup.getTopic("user-1", "topic-1"))
                .thenReturn(CompletableFuture.completedFuture(TopicProgress.of("topic-1", 1_200, 5)));
        when(commandGateway.send(any(RoomCommand.JoinRoomCommand.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        service.join("room-1", "user-1").join();

        ArgumentCaptor<RoomCommand.JoinRoomCommand> captor =
                ArgumentCaptor.forClass(RoomCommand.JoinRoomCommand.class);
        verify(commandGateway).send(captor.capture());
        RoomCommand.JoinRoomCommand command = captor.getValue();
        assertThat(command.roomId()).isEqualTo("room-1");
        assertThat(command.playerId()).isEqualTo("user-1");
        assertThat(command.player().playerName()).isEqualTo("Alice");
        assertThat(command.player().language()).isEqualTo(Language.FR);
        assertThat(command.player().rating()).isEqualTo(842);
        assertThat(command.player().topicXp()).isEqualTo(1_200);
    }

    @Test
    void join_fallsBackToNeutralProgressionWhenMissing() {
        stubRoom();
        when(profileLookup.get("user-1")).thenReturn(CompletableFuture.completedFuture(profile()));
        when(progressionLookup.get("user-1"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("no progression")));
        when(progressionLookup.getTopic("user-1", "topic-1"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("no topic progression")));
        when(commandGateway.send(any(RoomCommand.JoinRoomCommand.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        service.join("room-1", "user-1").join();

        ArgumentCaptor<RoomCommand.JoinRoomCommand> captor =
                ArgumentCaptor.forClass(RoomCommand.JoinRoomCommand.class);
        verify(commandGateway).send(captor.capture());
        assertThat(captor.getValue().player().rating()).isNull();
        assertThat(captor.getValue().player().topicXp()).isZero();
    }

    @Test
    void wizz_checksRoomExistenceAndSendsTheCommand() {
        stubRoom();
        when(commandGateway.send(any(RoomCommand.WizzRoomCommand.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        service.wizz("room-1", "user-1").join();

        ArgumentCaptor<RoomCommand.WizzRoomCommand> captor =
                ArgumentCaptor.forClass(RoomCommand.WizzRoomCommand.class);
        verify(commandGateway).send(captor.capture());
        assertThat(captor.getValue().roomId()).isEqualTo("room-1");
        assertThat(captor.getValue().playerId()).isEqualTo("user-1");
    }

    @Test
    void get_enrichesOpponentWithEloAndCountry() {
        when(queryGateway.query(any(RoomQuery.GetRoomById.class), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(Room.builder()
                        .roomId("room-1")
                        .topicId("topic-1")
                        .initiatorId("user-1")
                        .participantId("user-2")
                        .status(RoomStatus.WAITING_PLAYERS)
                        .createdAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .updatedAt(Instant.now())
                        .build()));
        when(queryGateway.query(any(TopicQuery.GetTopicsByIdsQuery.class), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(queryGateway.query(any(ProgressionQuery.GetProgressionsByIdsQuery.class), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(List.of(progress("user-2", 900, 10))));
        when(profileLookup.getAll(List.of("user-2")))
                .thenReturn(CompletableFuture.completedFuture(List.of(profile("user-2", "Bob", "FR"))));
        when(identityLookup.statuses(List.of("user-2")))
                .thenReturn(CompletableFuture.completedFuture(Map.of()));

        RoomView view = service.get("room-1", "user-1").join();

        assertThat(view.opponent()).isNotNull();
        assertThat(view.opponent().userId()).isEqualTo("user-2");
        assertThat(view.opponent().pseudonym()).isEqualTo("Bob");
        assertThat(view.opponent().rating()).isEqualTo(900);
        assertThat(view.opponent().provisional()).isFalse();
        assertThat(view.opponent().country()).isEqualTo("FR");
    }

    private void stubRoom() {
        when(queryGateway.query(any(RoomQuery.GetRoomById.class), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(Room.builder()
                        .roomId("room-1")
                        .topicId("topic-1")
                        .initiatorId("user-1")
                        .status(RoomStatus.WAITING_PLAYERS)
                        .createdAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .updatedAt(Instant.now())
                        .build()));
    }

    private static Profile profile() {
        return profile("user-1", "Alice", "FR");
    }

    private static Profile profile(String userId, String pseudonym, String country) {
        return new Profile(
                userId,
                userId + "@joutio.test",
                pseudonym,
                null,
                country,
                null,
                null,
                null,
                Language.FR,
                Instant.now(),
                Instant.now());
    }

    private static PlayerProgress progress() {
        return progress("user-1", 842, 10);
    }

    private static PlayerProgress progress(String userId, int rating, int ratedGames) {
        return PlayerProgress.builder()
                .userId(userId)
                .rating(rating)
                .ratedGames(ratedGames)
                .xpByTopic(Map.of())
                .badges(Set.of())
                .gamesPlayed(10)
                .wins(6)
                .losses(3)
                .draws(1)
                .bestScore(900)
                .currentWinStreak(2)
                .bestWinStreak(4)
                .updatedAt(Instant.now())
                .build();
    }
}
