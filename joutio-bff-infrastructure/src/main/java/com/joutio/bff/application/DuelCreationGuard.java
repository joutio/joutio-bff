package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.matchmaking.domain.model.Challenge;
import com.joutio.matchmaking.domain.model.ChallengeStatus;
import com.joutio.matchmaking.domain.model.Room;
import com.joutio.matchmaking.domain.model.RoomStatus;
import com.joutio.matchmaking.domain.query.ChallengeQuery;
import com.joutio.matchmaking.domain.query.RoomQuery;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Garde de création des défis et salons : empêche le spam et applique les quotas.
 *
 * <ul>
 *   <li><b>Défi</b> : un défi PENDING ne peut pas être dupliqué entre les deux mêmes joueurs sur
 *   le même sujet, dans aucun sens (symétrique) ; le nombre de défis PENDING <b>créés</b> par le
 *   joueur est plafonné par {@link DuelLimitPolicy}.</li>
 *   <li><b>Salon privé/partagé</b> : un joueur ne peut avoir qu'un salon WAITING_PLAYERS
 *   <b>créé</b> par sujet ; le nombre de salons WAITING_PLAYERS créés est plafonné par
 *   {@link DuelLimitPolicy}.</li>
 *   <li><b>Salon bot</b> : il ne partage pas les slots des salons privés/partagés — un salon
 *   privé ouvert ne bloque pas l'entraînement. Il possède son propre quota de salons bot
 *   ouverts, pour éviter le spam.</li>
 * </ul>
 *
 * <p>Les listes viennent des queries existantes : les défis où le joueur est invité sont utilisés
 * pour l'unicité symétrique mais ne comptent pas dans son quota (il n'en est pas le créateur).
 * Le contrôle est séquentiel ; deux créations strictement concurrentes sur des réplicas
 * différentes peuvent encore se glisser — un verrou fort demanderait un agrégat de réservation
 * dédié.</p>
 */
@Service
public class DuelCreationGuard {

    private final QueryGateway queryGateway;
    private final DuelLimitPolicy limits;

    public DuelCreationGuard(QueryGateway queryGateway, DuelLimitPolicy limits) {
        this.queryGateway = queryGateway;
        this.limits = limits;
    }

    /** Vérifie l'unicité symétrique (opposant + sujet) et le quota de défis créés. */
    public CompletableFuture<Void> ensureChallengeAllowed(String challengerId,
                                                          String opponentId,
                                                          String topicId) {
        return queryGateway
                .query(new ChallengeQuery.GetMyChallenges(challengerId),
                        QueryResponseTypes.multipleInstancesOf(Challenge.class))
                .thenAccept(challenges -> {
                    List<Challenge> active = activeChallenges(challenges);
                    boolean duplicate = active.stream().anyMatch(challenge ->
                            topicId.equals(challenge.topicId())
                                    && samePair(challenge, challengerId, opponentId));
                    if (duplicate) {
                        throw new BffProblems.ChallengeAlreadyPendingProblem(topicId, opponentId);
                    }
                    long created = active.stream()
                            .filter(challenge -> challengerId.equals(challenge.challengerId()))
                            .count();
                    int limit = limits.maxPendingChallenges(challengerId);
                    if (created >= limit) {
                        throw new BffProblems.ChallengeQuotaExceededProblem(limit);
                    }
                });
    }

    /** Vérifie l'unicité (initiateur + sujet) et le quota des salons privés/partagés. */
    public CompletableFuture<Void> ensureRoomAllowed(String initiatorId, String topicId) {
        return queryGateway
                .query(new RoomQuery.GetMyOpenRooms(initiatorId),
                        QueryResponseTypes.multipleInstancesOf(Room.class))
                .thenAccept(rooms -> {
                    List<Room> active = humanRooms(rooms, initiatorId);
                    boolean duplicate = active.stream()
                            .anyMatch(room -> topicId.equals(room.topicId()));
                    if (duplicate) {
                        String roomId = active.stream()
                                .filter(room -> topicId.equals(room.topicId()))
                                .findFirst()
                                .map(Room::roomId)
                                .orElse(null);
                        throw new BffProblems.RoomAlreadyOpenOnTopicProblem(topicId, roomId);
                    }
                    int limit = limits.maxOpenRooms(initiatorId);
                    if (active.size() >= limit) {
                        throw new BffProblems.RoomQuotaExceededProblem(limit);
                    }
                });
    }

    /**
     * Vérifie le quota des salons bot. Aucune unicité par sujet : un salon privé ouvert ne doit
     * jamais empêcher un entraînement contre le bot.
     */
    public CompletableFuture<Void> ensureBotRoomAllowed(String initiatorId) {
        return queryGateway
                .query(new RoomQuery.GetMyOpenRooms(initiatorId),
                        QueryResponseTypes.multipleInstancesOf(Room.class))
                .thenAccept(rooms -> {
                    List<Room> active = activeRooms(rooms).stream()
                            .filter(room -> initiatorId.equals(room.initiatorId()))
                            .filter(Room::vsBot)
                            .toList();
                    int limit = limits.maxOpenRooms(initiatorId);
                    if (active.size() >= limit) {
                        throw new BffProblems.RoomQuotaExceededProblem(limit);
                    }
                });
    }

    private static List<Challenge> activeChallenges(List<Challenge> challenges) {
        Instant now = Instant.now();
        return challenges.stream()
                .filter(challenge -> challenge.status() == ChallengeStatus.PENDING)
                .filter(challenge -> challenge.expiresAt() != null
                        && challenge.expiresAt().isAfter(now))
                .toList();
    }

    private static List<Room> activeRooms(List<Room> rooms) {
        Instant now = Instant.now();
        return rooms.stream()
                .filter(room -> room.status() == RoomStatus.WAITING_PLAYERS)
                .filter(room -> room.expiresAt() != null && room.expiresAt().isAfter(now))
                .toList();
    }

    /** Slots des salons privés/partagés : les salons bot ne les consomment pas. */
    private static List<Room> humanRooms(List<Room> rooms, String initiatorId) {
        return activeRooms(rooms).stream()
                .filter(room -> initiatorId.equals(room.initiatorId()))
                .filter(room -> !room.vsBot())
                .toList();
    }

    /** Vrai si le défi oppose exactement les deux joueurs, quel que soit le sens. */
    private static boolean samePair(Challenge challenge, String playerId, String opponentId) {
        return (playerId.equals(challenge.challengerId())
                && opponentId.equals(challenge.opponentId()))
                || (opponentId.equals(challenge.challengerId())
                && playerId.equals(challenge.opponentId()));
    }
}
