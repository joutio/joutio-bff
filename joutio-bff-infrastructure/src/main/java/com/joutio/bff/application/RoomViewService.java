package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.request.CreateBotRoomRequest;
import com.joutio.bff.infrastructure.in.api.request.CreateRoomRequest;
import com.joutio.bff.infrastructure.in.api.response.RoomView;
import com.joutio.bff.infrastructure.in.api.response.TopicRefView;
import com.joutio.bff.infrastructure.in.api.response.UserRefView;
import com.joutio.game.domain.model.BotDifficulty;
import com.joutio.matchmaking.domain.command.RoomCommand;
import com.joutio.matchmaking.domain.model.Room;
import com.joutio.matchmaking.domain.model.RoomPlayer;
import com.joutio.matchmaking.domain.query.RoomQuery;
import com.joutio.identity.domain.model.UserAccountStatus;
import com.joutio.microservice.core.domain.constant.JoutioConstants;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.profile.domain.model.PlayerProgress;
import com.joutio.profile.domain.model.Profile;
import com.joutio.profile.domain.model.TopicProgress;
import com.joutio.theme.domain.model.Topic;
import com.joutio.theme.domain.query.TopicQuery;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Salle (salle d'attente temps réel) : création (partagée ou contre le bot), consultation,
 * apparition, annulation. Le lien de partage se compose côté client via {@code /join/{roomId}}.
 */
@Service
public class RoomViewService {

    private final QueryGateway queryGateway;
    private final CommandGateway commandGateway;
    private final ProfileLookup profileLookup;
    private final ProgressionLookup progressionLookup;
    private final IdentityLookup identityLookup;
    private final DuelCreationGuard duelCreationGuard;

    public RoomViewService(QueryGateway queryGateway,
                           CommandGateway commandGateway,
                           ProfileLookup profileLookup,
                           ProgressionLookup progressionLookup,
                           IdentityLookup identityLookup,
                           DuelCreationGuard duelCreationGuard) {
        this.queryGateway = queryGateway;
        this.commandGateway = commandGateway;
        this.profileLookup = profileLookup;
        this.progressionLookup = progressionLookup;
        this.identityLookup = identityLookup;
        this.duelCreationGuard = duelCreationGuard;
    }

    /**
     * Ouvre une salle partagée (lien). Le défi nominatif passe par {@code POST /api/challenges} :
     * la salle nominative n'est créée qu'à l'acceptation (saga).
     */
    public CompletableFuture<String> create(String playerId, CreateRoomRequest request) {
        String roomId = UUID.randomUUID().toString();
        return duelCreationGuard.ensureRoomAllowed(playerId, request.topicId())
                .thenCompose(_ -> commandGateway
                        .send(new RoomCommand.CreateRoomCommand(
                                roomId, request.topicId(), playerId, null, false, null)))
                .thenApply(_ -> roomId);
    }

    /**
     * Ouvre une salle contre le bot : le second joueur est le bot, joint automatiquement par la
     * saga salle (l'humain apparaît ensuite côté client). Un salon privé ouvert sur le même sujet
     * ne bloque pas l'entraînement : seuls les salons bot sont comptés dans leur quota.
     */
    public CompletableFuture<String> createBot(String playerId, CreateBotRoomRequest request) {
        String roomId = UUID.randomUUID().toString();
        BotDifficulty difficulty = BotDifficulty.fromOrDefault(request.difficulty());
        return duelCreationGuard.ensureBotRoomAllowed(playerId)
                .thenCompose(_ -> commandGateway
                        .send(new RoomCommand.CreateRoomCommand(
                                roomId, request.topicId(), playerId, JoutioConstants.SYSTEM_USER_ID,
                                true, difficulty.name())))
                .thenApply(_ -> roomId);
    }

    public CompletableFuture<RoomView> get(String roomId, String viewerId) {
        return queryGateway
                .query(new RoomQuery.GetRoomById(roomId), QueryResponseTypes.instanceOf(Room.class))
                .thenCompose(room -> enrich(List.of(room), viewerId).thenApply(views -> views.getFirst()));
    }

    public CompletableFuture<List<RoomView>> mine(String playerId) {
        return queryGateway
                .query(new RoomQuery.GetMyOpenRooms(playerId),
                        QueryResponseTypes.multipleInstancesOf(Room.class))
                .thenCompose(rooms -> enrich(rooms, playerId));
    }

    /**
     * Le joueur <b>apparaît</b> dans la salle : présence temps réel et, pour le second humain,
     * enregistrement comme participant. C'est la seule action d'entrée, émise par le client quand
     * l'utilisateur est réellement dans la salle.
     * <p>
     * Le snapshot de profil ({@code RoomPlayer}) est <b>préparé ici</b> (profils + progression,
     * caches Caffeine) et porté par la commande : la salle ne fait aucune I/O pour résoudre les
     * joueurs (voir {@code best-practices/.backend/command-preparation.md}).
     */
    public CompletableFuture<Void> join(String roomId, String playerId) {
        return queryGateway
                .query(new RoomQuery.GetRoomById(roomId), QueryResponseTypes.instanceOf(Room.class))
                .thenCompose(room -> roomPlayer(playerId, room.topicId()))
                .thenCompose(player -> commandGateway
                        .send(new RoomCommand.JoinRoomCommand(roomId, playerId, player))
                        .thenAccept(_ -> {
                        }));
    }

    /**
     * Snapshot de profil du joueur qui apparaît. Une progression absente ne bloque pas l'entrée
     * (repli XP de sujet 0), un profil introuvable, si.
     */
    private CompletableFuture<RoomPlayer> roomPlayer(String playerId, String topicId) {
        CompletableFuture<Profile> profileFuture = profileLookup.get(playerId);
        CompletableFuture<PlayerProgress> progressFuture = progressionLookup.get(playerId)
                .exceptionally(_ -> null);
        CompletableFuture<TopicProgress> topicProgressFuture = progressionLookup
                .getTopic(playerId, topicId)
                .exceptionally(_ -> null);
        return profileFuture
                .thenCombine(progressFuture, (profile, progress) -> new PlayerWithProgress(profile, progress))
                .thenCombine(topicProgressFuture, (player, topicProgress) -> new RoomPlayer(
                        player.profile().userId(),
                        player.profile().pseudonym(),
                        player.profile().language(),
                        topicProgress == null ? 0 : Math.max(0, topicProgress.xp()),
                        player.progress() == null ? null : player.progress().rating()));
    }

    private record PlayerWithProgress(Profile profile, PlayerProgress progress) {
    }

    public CompletableFuture<Void> leave(String roomId, String playerId) {
        return requireRoom(roomId)
                .thenCompose(_ -> commandGateway
                        .send(new RoomCommand.LeaveRoomCommand(roomId, playerId))
                        .thenAccept(_ -> {
                        }));
    }

    public CompletableFuture<Void> cancel(String roomId, String playerId) {
        return requireRoom(roomId)
                .thenCompose(_ -> commandGateway
                        .send(new RoomCommand.CancelRoomCommand(roomId, playerId))
                        .thenAccept(_ -> {
                        }));
    }

    /**
     * Relance (« wizz ») de l'adversaire absent : pur temps réel (aucune préparation déclenchée).
     * Les invariants (émetteur présent, cible humaine absente, cadence 10 s) vivent dans l'agrégat
     * — une relance refusée remonte le {@code Problem} métier mappé par le SDK.
     */
    public CompletableFuture<Void> wizz(String roomId, String playerId) {
        return requireRoom(roomId)
                .thenCompose(_ -> commandGateway
                        .send(new RoomCommand.WizzRoomCommand(roomId, playerId))
                        .thenAccept(_ -> {
                        }));
    }

    /**
     * Pré-vérification : une salle purgée (état terminal + rétention) doit répondre 404,
     * pas une {@code AggregateDeletedException} non mappée par le SDK.
     */
    private CompletableFuture<Void> requireRoom(String roomId) {
        return queryGateway
                .query(new RoomQuery.GetRoomById(roomId), QueryResponseTypes.instanceOf(Room.class))
                .thenAccept(_ -> {
                });
    }

    private CompletableFuture<List<RoomView>> enrich(List<Room> rooms, String viewerId) {
        List<String> topicIds = rooms.stream().map(Room::topicId).distinct().toList();
        List<String> opponentIds = rooms.stream()
                .map(room -> opponentIdOf(viewerId, room))
                .filter(id -> id != null && !JoutioConstants.SYSTEM_USER_ID.equals(id))
                .distinct()
                .toList();

        CompletableFuture<List<Topic>> topicsFuture = topicIds.isEmpty()
                ? CompletableFuture.completedFuture(List.of())
                : queryGateway.query(new TopicQuery.GetTopicsByIdsQuery(topicIds),
                        QueryResponseTypes.multipleInstancesOf(Topic.class));
        CompletableFuture<List<Profile>> profilesFuture = opponentIds.isEmpty()
                ? CompletableFuture.completedFuture(List.of())
                : profileLookup.getAll(opponentIds);
        CompletableFuture<Map<String, UserAccountStatus>> statusesFuture = identityLookup.statuses(opponentIds)
                .exceptionally(_ -> Map.<String, UserAccountStatus>of());

        return CompletableFuture.allOf(topicsFuture, profilesFuture, statusesFuture).thenApply(_ -> {
            Map<String, Topic> topicById = topicsFuture.join().stream()
                    .collect(Collectors.toMap(Topic::topicId, Function.identity(), (first, _) -> first, LinkedHashMap::new));
            Map<String, Profile> profileById = profilesFuture.join().stream()
                    .collect(Collectors.toMap(Profile::userId, Function.identity(), (first, _) -> first, LinkedHashMap::new));
            Map<String, UserAccountStatus> statusById = statusesFuture.join();

            return rooms.stream()
                    .map(room -> toView(room, viewerId, topicById, profileById, statusById))
                    .toList();
        });
    }

    private static RoomView toView(Room room,
                                   String viewerId,
                                   Map<String, Topic> topicById,
                                   Map<String, Profile> profileById,
                                   Map<String, UserAccountStatus> statusById) {
        String opponentId = opponentIdOf(viewerId, room);
        Profile opponent = opponentId == null ? null : profileById.get(opponentId);
        UserAccountStatus status = opponentId == null ? null : statusById.get(opponentId);
        TopicRefView topic = TopicViews.toRef(room.topicId(), topicById.get(room.topicId()));

        return new RoomView(
                room.roomId(),
                topic,
                room.status(),
                room.vsBot(),
                viewerId.equals(room.initiatorId()),
                opponentRef(opponentId, opponent, status),
                room.initiatorPresent(),
                room.participantPresent(),
                room.gameId(),
                room.createdAt(),
                room.expiresAt(),
                room.updatedAt());
    }

    /** Le bot n'a pas de profil : référence synthétique stable (nom système, sans avatar). */
    private static UserRefView opponentRef(String opponentId,
                                           Profile opponent,
                                           UserAccountStatus status) {
        if (opponent != null) {
            return new UserRefView(
                    opponent.userId(),
                    opponent.pseudonym(),
                    opponent.avatarOptions(),
                    status != null && status.suspended(Instant.now()));
        }
        if (JoutioConstants.SYSTEM_USER_ID.equals(opponentId)) {
            return new UserRefView(JoutioConstants.SYSTEM_USER_ID, JoutioConstants.SYSTEM_USER_NAME, null);
        }
        return null;
    }

    /**
     * L'adversaire du point de vue du viewer : le participant s'il est là, sinon l'invité du défi
     * nominatif (l'initiateur voit ainsi la cible avant son apparition en salle).
     */
    private static String opponentIdOf(String viewerId, Room room) {
        if (viewerId.equals(room.initiatorId())) {
            return room.participantId() != null ? room.participantId() : room.opponentId();
        }
        return room.initiatorId();
    }
}
