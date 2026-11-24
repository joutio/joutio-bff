package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.matchmaking.domain.model.Room;
import com.joutio.matchmaking.domain.model.RoomOrigin;
import com.joutio.matchmaking.domain.model.RoomStatus;
import com.joutio.matchmaking.domain.query.RoomQuery;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Garde de création des défis et salons : empêche le spam et applique les quotas, sur une seule lecture des salles
 * ouvertes du joueur ({@code GetMyOpenRooms}). L'échéance des salles est tenue par matchmaking : aucune salle n'est
 * filtrée ici sur l'heure.
 *
 * <ul>
 *   <li><b>Défi</b> (salle {@code CHALLENGE}/{@code REMATCH} en {@code INVITED}) : pas de second défi entre les deux
 *   mêmes joueurs sur le même sujet, dans aucun sens ; le nombre de défis <b>envoyés</b> en attente est plafonné par
 *   {@link DuelLimitPolicy}.</li>
 *   <li><b>Salon partagé</b> ({@code SHARED} en {@code WAITING_PLAYERS}) : un seul par sujet, et un nombre plafonné.</li>
 *   <li><b>Salon bot</b> : son propre quota, pour qu'un salon partagé ouvert n'empêche jamais l'entraînement.</li>
 * </ul>
 *
 * <p>Deux créations strictement concurrentes sur des répliques différentes peuvent encore se glisser : un verrou
 * fort demanderait un agrégat de réservation dédié.</p>
 */
@Service
public class DuelCreationGuard {

    private final QueryGateway queryGateway;
    private final DuelLimitPolicy limits;

    public DuelCreationGuard(QueryGateway queryGateway, DuelLimitPolicy limits) {
        this.queryGateway = queryGateway;
        this.limits = limits;
    }

    /** Vérifie l'unicité symétrique (adversaire + sujet) et le quota de défis envoyés en attente. */
    public CompletableFuture<Void> ensureChallengeAllowed(String challengerId, String opponentId, String topicId) {
        return openRooms(challengerId)
                .thenCombine(limits.maxPendingChallenges(challengerId), (rooms, limit) -> {
                    List<Room> invitations = rooms.stream().filter(DuelCreationGuard::isPendingInvitation).toList();
                    boolean duplicate = invitations.stream()
                            .anyMatch(room -> topicId.equals(room.topicId()) && samePair(room, challengerId, opponentId));
                    if (duplicate) {
                        throw new BffProblems.ChallengeAlreadyPendingProblem(topicId, opponentId);
                    }
                    long sent = invitations.stream().filter(room -> challengerId.equals(room.initiatorId())).count();
                    if (sent >= limit) {
                        throw new BffProblems.ChallengeQuotaExceededProblem(limit);
                    }
                    return null;
                });
    }

    /** Vérifie l'unicité (initiateur + sujet) et le quota des salons partagés. */
    public CompletableFuture<Void> ensureRoomAllowed(String initiatorId, String topicId) {
        return openRooms(initiatorId)
                .thenCombine(limits.maxOpenRooms(initiatorId), (rooms, limit) -> {
                    List<Room> shared = waitingRoomsCreatedBy(rooms, initiatorId, RoomOrigin.SHARED);
                    shared.stream()
                            .filter(room -> topicId.equals(room.topicId()))
                            .findFirst()
                            .ifPresent(room -> {
                                throw new BffProblems.RoomAlreadyOpenOnTopicProblem(topicId, room.roomId());
                            });
                    if (shared.size() >= limit) {
                        throw new BffProblems.RoomQuotaExceededProblem(limit);
                    }
                    return null;
                });
    }

    /** Vérifie le quota des salons bot. Aucune unicité par sujet. */
    public CompletableFuture<Void> ensureBotRoomAllowed(String initiatorId) {
        return openRooms(initiatorId).thenAccept(rooms -> {
            int limit = limits.maxBotRooms();
            if (waitingRoomsCreatedBy(rooms, initiatorId, RoomOrigin.BOT).size() >= limit) {
                throw new BffProblems.RoomQuotaExceededProblem(limit);
            }
        });
    }

    private CompletableFuture<List<Room>> openRooms(String playerId) {
        return queryGateway.query(
                new RoomQuery.GetMyOpenRooms(playerId), QueryResponseTypes.multipleInstancesOf(Room.class));
    }

    private static boolean isPendingInvitation(Room room) {
        return room.status() == RoomStatus.INVITED;
    }

    private static List<Room> waitingRoomsCreatedBy(List<Room> rooms, String initiatorId, RoomOrigin origin) {
        return rooms.stream()
                .filter(room -> room.status() == RoomStatus.WAITING_PLAYERS)
                .filter(room -> room.origin() == origin)
                .filter(room -> initiatorId.equals(room.initiatorId()))
                .toList();
    }

    /** Vrai si la salle oppose exactement les deux joueurs, quel que soit le sens. */
    private static boolean samePair(Room room, String playerId, String opponentId) {
        return (playerId.equals(room.initiatorId()) && opponentId.equals(room.opponentId()))
                || (opponentId.equals(room.initiatorId()) && playerId.equals(room.opponentId()));
    }
}
