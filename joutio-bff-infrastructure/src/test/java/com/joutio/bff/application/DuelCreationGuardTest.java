package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.matchmaking.domain.model.Challenge;
import com.joutio.matchmaking.domain.model.ChallengeStatus;
import com.joutio.matchmaking.domain.model.Room;
import com.joutio.matchmaking.domain.model.RoomStatus;
import com.joutio.matchmaking.domain.query.ChallengeQuery;
import com.joutio.matchmaking.domain.query.RoomQuery;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Règles anti-spam : unicité symétrique (joueur + joueur + sujet), un salon privé/partagé créé
 * par sujet, et quotas des intentions <b>créées</b> par le joueur. Les salons bot ont leur
 * propre quota et ne bloquent jamais un salon privé (ni l'inverse).
 */
class DuelCreationGuardTest {

    private static final String ME = "me";
    private static final String OTHER = "other";
    private static final String TOPIC = "topic-1";
    private static final Instant NOW = Instant.now();

    private final QueryGateway queryGateway = mock(QueryGateway.class);
    private final DuelLimitPolicy limits = mock(DuelLimitPolicy.class);
    private final DuelCreationGuard guard = new DuelCreationGuard(queryGateway, limits);

    @BeforeEach
    void setUp() {
        when(limits.maxPendingChallenges(any())).thenReturn(5);
        when(limits.maxOpenRooms(any())).thenReturn(5);
    }

    @Test
    void challenge_refusesSymmetricDuplicateOnSameTopic() {
        stubChallenges(challenge("c1", OTHER, ME, TOPIC, ChallengeStatus.PENDING, NOW.plusSeconds(60)));

        assertThatThrownBy(() -> guard.ensureChallengeAllowed(ME, OTHER, TOPIC).join())
                .hasCauseInstanceOf(BffProblems.ChallengeAlreadyPendingProblem.class);
    }

    @Test
    void challenge_allowsSameOpponentOnAnotherTopic() {
        stubChallenges(challenge("c1", ME, OTHER, "topic-2", ChallengeStatus.PENDING, NOW.plusSeconds(60)));

        assertThatCode(() -> guard.ensureChallengeAllowed(ME, OTHER, TOPIC).join())
                .doesNotThrowAnyException();
    }

    @Test
    void challenge_countsOnlyCreatedChallengesForQuota() {
        when(limits.maxPendingChallenges(ME)).thenReturn(1);
        stubChallenges(
                challenge("c1", ME, "a", "topic-2", ChallengeStatus.PENDING, NOW.plusSeconds(60)),
                challenge("c2", "b", ME, "topic-3", ChallengeStatus.PENDING, NOW.plusSeconds(60)));

        assertThatThrownBy(() -> guard.ensureChallengeAllowed(ME, OTHER, TOPIC).join())
                .hasCauseInstanceOf(BffProblems.ChallengeQuotaExceededProblem.class);
    }

    @Test
    void challenge_ignoresExpiredAndResolvedForQuota() {
        when(limits.maxPendingChallenges(ME)).thenReturn(1);
        stubChallenges(
                challenge("c1", ME, "a", "topic-2", ChallengeStatus.PENDING, NOW.minusSeconds(1)),
                challenge("c2", ME, "b", "topic-3", ChallengeStatus.DECLINED, NOW.plusSeconds(60)));

        assertThatCode(() -> guard.ensureChallengeAllowed(ME, OTHER, TOPIC).join())
                .doesNotThrowAnyException();
    }

    @Test
    void room_refusesSecondWaitingRoomCreatedOnSameTopic() {
        stubRooms(room("r1", ME, TOPIC, RoomStatus.WAITING_PLAYERS, NOW.plusSeconds(60)));

        assertThatThrownBy(() -> guard.ensureRoomAllowed(ME, TOPIC).join())
                .hasCauseInstanceOf(BffProblems.RoomAlreadyOpenOnTopicProblem.class);
    }

    @Test
    void room_ignoresRoomWherePlayerIsOnlyInvited() {
        stubRooms(room("r1", OTHER, TOPIC, RoomStatus.WAITING_PLAYERS, NOW.plusSeconds(60)));

        assertThatCode(() -> guard.ensureRoomAllowed(ME, TOPIC).join())
                .doesNotThrowAnyException();
    }

    @Test
    void room_appliesCreatedRoomsQuota() {
        when(limits.maxOpenRooms(ME)).thenReturn(1);
        stubRooms(
                room("r1", ME, "topic-2", RoomStatus.WAITING_PLAYERS, NOW.plusSeconds(60)),
                room("r2", ME, "topic-3", RoomStatus.WAITING_PLAYERS, NOW.plusSeconds(60)));

        assertThatThrownBy(() -> guard.ensureRoomAllowed(ME, TOPIC).join())
                .hasCauseInstanceOf(BffProblems.RoomQuotaExceededProblem.class);
    }

    @Test
    void room_ignoresExpiredAndTerminalRooms() {
        when(limits.maxOpenRooms(ME)).thenReturn(1);
        stubRooms(
                room("r1", ME, "topic-2", RoomStatus.WAITING_PLAYERS, NOW.minusSeconds(1)),
                room("r2", ME, "topic-3", RoomStatus.CANCELLED, NOW.plusSeconds(60)));

        assertThatCode(() -> guard.ensureRoomAllowed(ME, TOPIC).join())
                .doesNotThrowAnyException();
    }

    @Test
    void room_ignoresBotRoomForHumanTopicUniquenessAndQuota() {
        when(limits.maxOpenRooms(ME)).thenReturn(1);
        stubRooms(botRoom("r1", ME, TOPIC, RoomStatus.WAITING_PLAYERS, NOW.plusSeconds(60)));

        assertThatCode(() -> guard.ensureRoomAllowed(ME, TOPIC).join())
                .doesNotThrowAnyException();
    }

    @Test
    void botRoom_allowedWhileHumanRoomIsOpenOnTheSameTopic() {
        stubRooms(room("r1", ME, TOPIC, RoomStatus.WAITING_PLAYERS, NOW.plusSeconds(60)));

        assertThatCode(() -> guard.ensureBotRoomAllowed(ME).join())
                .doesNotThrowAnyException();
    }

    @Test
    void botRoom_hasItsOwnQuota() {
        when(limits.maxOpenRooms(ME)).thenReturn(1);
        stubRooms(botRoom("r1", ME, TOPIC, RoomStatus.WAITING_PLAYERS, NOW.plusSeconds(60)));

        assertThatThrownBy(() -> guard.ensureBotRoomAllowed(ME).join())
                .hasCauseInstanceOf(BffProblems.RoomQuotaExceededProblem.class);
    }

    private void stubChallenges(Challenge... challenges) {
        when(queryGateway.query(
                any(ChallengeQuery.GetMyChallenges.class),
                ArgumentMatchers.<ResponseType<List<Challenge>>>any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(challenges)));
    }

    private void stubRooms(Room... rooms) {
        when(queryGateway.query(
                any(RoomQuery.GetMyOpenRooms.class),
                ArgumentMatchers.<ResponseType<List<Room>>>any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(rooms)));
    }

    private static Challenge challenge(String id,
                                       String challenger,
                                       String opponent,
                                       String topic,
                                       ChallengeStatus status,
                                       Instant expiresAt) {
        return Challenge.builder()
                .challengeId(id)
                .topicId(topic)
                .challengerId(challenger)
                .opponentId(opponent)
                .status(status)
                .createdAt(NOW)
                .expiresAt(expiresAt)
                .build();
    }

    private static Room room(String id,
                             String initiator,
                             String topic,
                             RoomStatus status,
                             Instant expiresAt) {
        return room(id, initiator, topic, status, expiresAt, false);
    }

    private static Room botRoom(String id,
                                String initiator,
                                String topic,
                                RoomStatus status,
                                Instant expiresAt) {
        return room(id, initiator, topic, status, expiresAt, true);
    }

    private static Room room(String id,
                             String initiator,
                             String topic,
                             RoomStatus status,
                             Instant expiresAt,
                             boolean vsBot) {
        return Room.builder()
                .roomId(id)
                .topicId(topic)
                .initiatorId(initiator)
                .vsBot(vsBot)
                .status(status)
                .createdAt(NOW)
                .expiresAt(expiresAt)
                .updatedAt(NOW)
                .build();
    }
}
