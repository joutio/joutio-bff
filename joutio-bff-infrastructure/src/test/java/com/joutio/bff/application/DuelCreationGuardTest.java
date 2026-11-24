package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.matchmaking.domain.model.Room;
import com.joutio.matchmaking.domain.model.RoomOrigin;
import com.joutio.matchmaking.domain.model.RoomStatus;
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
 * Règles anti-spam, lues sur les salles ouvertes du joueur : un seul défi en attente par paire et par sujet (dans
 * les deux sens), un salon partagé par sujet, et des quotas par type de salle créée. Les salons bot ont leur propre
 * quota et ne bloquent jamais un salon partagé (ni l'inverse).
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
        when(limits.maxPendingChallenges(any())).thenReturn(CompletableFuture.completedFuture(5));
        when(limits.maxOpenRooms(any())).thenReturn(CompletableFuture.completedFuture(5));
        when(limits.maxBotRooms()).thenReturn(5);
    }

    @Test
    void challenge_refusesSymmetricDuplicateOnSameTopic() {
        stubRooms(invitation("r1", OTHER, ME, TOPIC));

        assertThatThrownBy(() -> guard.ensureChallengeAllowed(ME, OTHER, TOPIC).join())
                .hasCauseInstanceOf(BffProblems.ChallengeAlreadyPendingProblem.class);
    }

    @Test
    void challenge_allowsSameOpponentOnAnotherTopic() {
        stubRooms(invitation("r1", ME, OTHER, "topic-2"));

        assertThatCode(() -> guard.ensureChallengeAllowed(ME, OTHER, TOPIC).join()).doesNotThrowAnyException();
    }

    @Test
    void challenge_allowsANewChallengeOnceTheLastOneWasAccepted() {
        stubRooms(room("r1", ME, OTHER, TOPIC, RoomOrigin.CHALLENGE, RoomStatus.WAITING_PLAYERS));

        assertThatCode(() -> guard.ensureChallengeAllowed(ME, OTHER, TOPIC).join()).doesNotThrowAnyException();
    }

    @Test
    void challenge_countsOnlySentInvitationsForQuota() {
        when(limits.maxPendingChallenges(ME)).thenReturn(CompletableFuture.completedFuture(1));
        stubRooms(invitation("r1", ME, "a", "topic-2"), invitation("r2", "b", ME, "topic-3"));

        assertThatThrownBy(() -> guard.ensureChallengeAllowed(ME, OTHER, TOPIC).join())
                .hasCauseInstanceOf(BffProblems.ChallengeQuotaExceededProblem.class);
    }

    @Test
    void challenge_receivedInvitationsDoNotConsumeTheQuota() {
        when(limits.maxPendingChallenges(ME)).thenReturn(CompletableFuture.completedFuture(1));
        stubRooms(invitation("r1", "b", ME, "topic-3"));

        assertThatCode(() -> guard.ensureChallengeAllowed(ME, OTHER, TOPIC).join()).doesNotThrowAnyException();
    }

    @Test
    void room_refusesSecondSharedRoomOnSameTopic() {
        stubRooms(shared("r1", ME, TOPIC));

        assertThatThrownBy(() -> guard.ensureRoomAllowed(ME, TOPIC).join())
                .hasCauseInstanceOf(BffProblems.RoomAlreadyOpenOnTopicProblem.class);
    }

    @Test
    void room_ignoresRoomsOfOtherOrigins() {
        when(limits.maxOpenRooms(ME)).thenReturn(CompletableFuture.completedFuture(1));
        stubRooms(
                room("r1", ME, OTHER, TOPIC, RoomOrigin.CHALLENGE, RoomStatus.WAITING_PLAYERS),
                room("r2", ME, OTHER, TOPIC, RoomOrigin.MATCHMAKING, RoomStatus.WAITING_PLAYERS),
                bot("r3", ME, TOPIC));

        assertThatCode(() -> guard.ensureRoomAllowed(ME, TOPIC).join()).doesNotThrowAnyException();
    }

    @Test
    void room_ignoresSharedRoomCreatedByAnotherPlayer() {
        stubRooms(shared("r1", OTHER, TOPIC));

        assertThatCode(() -> guard.ensureRoomAllowed(ME, TOPIC).join()).doesNotThrowAnyException();
    }

    @Test
    void room_appliesSharedRoomsQuota() {
        when(limits.maxOpenRooms(ME)).thenReturn(CompletableFuture.completedFuture(1));
        stubRooms(shared("r1", ME, "topic-2"));

        assertThatThrownBy(() -> guard.ensureRoomAllowed(ME, TOPIC).join())
                .hasCauseInstanceOf(BffProblems.RoomQuotaExceededProblem.class);
    }

    @Test
    void botRoom_allowedWhileSharedRoomIsOpenOnTheSameTopic() {
        stubRooms(shared("r1", ME, TOPIC));

        assertThatCode(() -> guard.ensureBotRoomAllowed(ME).join()).doesNotThrowAnyException();
    }

    @Test
    void botRoom_hasItsOwnQuota() {
        when(limits.maxBotRooms()).thenReturn(1);
        stubRooms(bot("r1", ME, TOPIC));

        assertThatThrownBy(() -> guard.ensureBotRoomAllowed(ME).join())
                .hasCauseInstanceOf(BffProblems.RoomQuotaExceededProblem.class);
    }

    private void stubRooms(Room... rooms) {
        when(queryGateway.query(
                any(RoomQuery.GetMyOpenRooms.class),
                ArgumentMatchers.<ResponseType<List<Room>>>any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(rooms)));
    }

    private static Room invitation(String id, String initiator, String opponent, String topic) {
        return room(id, initiator, opponent, topic, RoomOrigin.CHALLENGE, RoomStatus.INVITED);
    }

    private static Room shared(String id, String initiator, String topic) {
        return room(id, initiator, null, topic, RoomOrigin.SHARED, RoomStatus.WAITING_PLAYERS);
    }

    private static Room bot(String id, String initiator, String topic) {
        return room(id, initiator, null, topic, RoomOrigin.BOT, RoomStatus.WAITING_PLAYERS).toBuilder()
                .vsBot(true)
                .build();
    }

    private static Room room(
            String id, String initiator, String opponent, String topic, RoomOrigin origin, RoomStatus status) {
        return Room.builder()
                .roomId(id)
                .topicId(topic)
                .initiatorId(initiator)
                .opponentId(opponent)
                .origin(origin)
                .status(status)
                .createdAt(NOW)
                .expiresAt(NOW.plusSeconds(60))
                .updatedAt(NOW)
                .build();
    }
}
