package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.request.CreateBotRoomRequest;
import com.joutio.bff.infrastructure.in.api.request.CreateChallengeRequest;
import com.joutio.bff.infrastructure.in.api.request.CreateRoomRequest;
import com.joutio.bff.infrastructure.in.api.response.RoomOpponentView;
import com.joutio.bff.infrastructure.in.api.response.RoomView;
import com.joutio.bff.infrastructure.in.api.response.RoomAccess;
import com.joutio.bff.infrastructure.in.api.response.TopicRefView;
import com.joutio.game.domain.model.BotDifficulty;
import com.joutio.game.domain.model.Game;
import com.joutio.game.domain.model.GamePlayerType;
import com.joutio.game.domain.query.GameQuery;
import com.joutio.identity.domain.model.UserAccountStatus;
import com.joutio.matchmaking.domain.command.RoomCommand;
import com.joutio.matchmaking.domain.model.Room;
import com.joutio.matchmaking.domain.model.RoomCancelReason;
import com.joutio.matchmaking.domain.model.RoomLeaveReason;
import com.joutio.matchmaking.domain.model.RoomOrigin;
import com.joutio.matchmaking.domain.model.RoomPlayer;
import com.joutio.matchmaking.domain.query.RoomQuery;
import com.joutio.microservice.core.domain.constant.JoutioConstants;
import com.joutio.microservice.core.domain.model.event.EventEnvelope;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.profile.domain.model.PlayerProgress;
import com.joutio.profile.domain.model.Profile;
import com.joutio.profile.domain.model.TopicProgress;
import com.joutio.profile.domain.query.ProgressionQuery;
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
 * Salles : création (défi ou revanche, salon partagé, bot), consultation, réponse à une invitation, apparition,
 * relance et annulation. Un défi est une salle {@code INVITED} : l'invité l'accepte ou la refuse. Le lien de partage
 * se compose côté client ({@code go.joutio.com/j/{roomId}}).
 */
@Service
public class RoomViewService {

    private final QueryGateway queryGateway;
    private final UserBlockService userBlockService;
    private final CommandGateway commandGateway;
    private final ProfileLookup profileLookup;
    private final ProgressionLookup progressionLookup;
    private final IdentityLookup identityLookup;
    private final DuelCreationGuard duelCreationGuard;

    public RoomViewService(
            QueryGateway queryGateway,
            CommandGateway commandGateway,
            ProfileLookup profileLookup,
            ProgressionLookup progressionLookup,
            IdentityLookup identityLookup,
            DuelCreationGuard duelCreationGuard,
            UserBlockService userBlockService) {
        this.userBlockService = userBlockService;
        this.queryGateway = queryGateway;
        this.commandGateway = commandGateway;
        this.profileLookup = profileLookup;
        this.progressionLookup = progressionLookup;
        this.identityLookup = identityLookup;
        this.duelCreationGuard = duelCreationGuard;
    }

    /**
     * Défi (ou revanche si {@code gameId}) : la salle naît {@code INVITED}, l'invité répond. L'adversaire doit exister
     * et être actif (statut identity lu avant le profil, déjà supprimé pour un compte effacé), le sujet jouable, aucun
     * blocage entre les deux, et la garde de création respectée. C'est le seul point où le blocage est vérifié pour
     * une salle nominative : un blocage ultérieur annule la salle (matchmaking).
     */
    public CompletableFuture<String> createChallenge(String challengerId, CreateChallengeRequest request) {
        String roomId = UUID.randomUUID().toString();
        boolean rematch = request.gameId() != null && !request.gameId().isBlank();
        return identityLookup
                .status(request.opponentId())
                .exceptionally(_ -> null)
                .thenCompose(account -> {
                    if (account != null && account.deleted()) {
                        throw new BffProblems.ChallengeOpponentDeletedProblem(request.opponentId());
                    }
                    if (account != null && account.suspended(Instant.now())) {
                        throw new BffProblems.ChallengeOpponentSuspendedProblem(request.opponentId());
                    }
                    return profileLookup.get(request.opponentId());
                })
                .thenCompose(_ -> TopicAccessService.requirePlayable(queryGateway, request.topicId()))
                .thenCompose(_ -> userBlockService.requireNotBlocked(challengerId, request.opponentId()))
                .thenCompose(_ -> rematch ? validateRematch(challengerId, request) : CompletableFuture.completedFuture(null))
                .thenCompose(_ -> duelCreationGuard.ensureChallengeAllowed(
                        challengerId, request.opponentId(), request.topicId()))
                .thenCompose(_ -> commandGateway.send(new RoomCommand.CreateRoomCommand(
                        roomId,
                        request.topicId(),
                        challengerId,
                        request.opponentId(),
                        false,
                        null,
                        rematch ? RoomOrigin.REMATCH : RoomOrigin.CHALLENGE,
                        rematch ? request.gameId() : null)))
                .thenApply(_ -> roomId);
    }

    /**
     * Revanche : la partie d'origine existe (404 via le problème game), le lanceur y a joué, c'est un duel entre
     * humains et {@code opponentId} est l'autre joueur.
     */
    private CompletableFuture<Void> validateRematch(String challengerId, CreateChallengeRequest request) {
        return queryGateway
                .query(new GameQuery.GetGameByIdQuery(request.gameId()), QueryResponseTypes.instanceOf(Game.class))
                .thenAccept(game -> {
                    boolean participated = challengerId.equals(game.player1Id()) || challengerId.equals(game.player2Id());
                    if (!participated) {
                        throw new BffProblems.RematchNotAllowedProblem(request.gameId(), challengerId);
                    }
                    if (GamePlayerType.BOT.equals(game.opponent())) {
                        throw new BffProblems.InvalidRematchProblem("A bot duel cannot be rematched with a challenge");
                    }
                    String opponentId = challengerId.equals(game.player1Id()) ? game.player2Id() : game.player1Id();
                    if (!opponentId.equals(request.opponentId())) {
                        throw new BffProblems.InvalidRematchProblem(
                                "opponentId must be the other player of the source game");
                    }
                });
    }

    /** Ouvre un salon partagé par lien : le premier qui entre devient l'adversaire. */
    public CompletableFuture<String> create(String playerId, CreateRoomRequest request) {
        String roomId = UUID.randomUUID().toString();
        return TopicAccessService.requirePlayable(queryGateway, request.topicId())
                .thenCompose(_ -> duelCreationGuard.ensureRoomAllowed(playerId, request.topicId()))
                .thenCompose(
                        _ ->
                                commandGateway.send(
                                        new RoomCommand.CreateRoomCommand(
                                                roomId,
                                                request.topicId(),
                                                playerId,
                                                null,
                                                false,
                                                null,
                                                RoomOrigin.SHARED,
                                                null)))
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
        return TopicAccessService.requirePlayable(queryGateway, request.topicId())
                .thenCompose(_ -> duelCreationGuard.ensureBotRoomAllowed(playerId))
                .thenCompose(
                        _ ->
                                commandGateway.send(
                                        new RoomCommand.CreateRoomCommand(
                                                roomId,
                                                request.topicId(),
                                                playerId,
                                                JoutioConstants.SYSTEM_USER_ID,
                                                true,
                                                difficulty.name(),
                                                RoomOrigin.BOT,
                                                null)))
                .thenApply(_ -> roomId);
    }

    public CompletableFuture<RoomView> get(String roomId, String viewerId) {
        return queryGateway
                .query(new RoomQuery.GetRoomById(roomId), QueryResponseTypes.instanceOf(Room.class))
                .thenCompose(
                        room ->
                                enrich(List.of(room), viewerId)
                                        .thenApply(views -> views.getFirst()));
    }

    public CompletableFuture<List<RoomView>> mine(String playerId) {
        return queryGateway
                .query(
                        new RoomQuery.GetMyOpenRooms(playerId),
                        QueryResponseTypes.multipleInstancesOf(Room.class))
                .thenCompose(rooms -> enrich(rooms, playerId));
    }

    /**
     * Le joueur <b>apparaît</b> dans la salle : présence temps réel et, pour le second humain,
     * enregistrement comme participant. Appelée par {@code PresenceChannelListener} quand le joueur
     * s'abonne au canal de présence du salon ({@code presenceId} = session STOMP).
     *
     * <p>Le snapshot de profil ({@code RoomPlayer}) est <b>préparé ici</b> (profils + progression,
     * caches Caffeine) et porté par la commande : la salle ne fait aucune I/O pour résoudre les
     * joueurs (voir {@code docs/backend/axon.md}, préparation des commandes).
     */
    public CompletableFuture<Void> join(String roomId, String playerId, String presenceId) {
        return queryGateway
                .query(new RoomQuery.GetRoomById(roomId), QueryResponseTypes.instanceOf(Room.class))
                .thenCompose(
                        room ->
                                (room.gameId() == null
                                                ? TopicAccessService.requirePlayable(
                                                        queryGateway, room.topicId())
                                                : CompletableFuture.<Void>completedFuture(null))
                                        // Salon partagé d'un joueur en relation de blocage : refusé, sans dire
                                        // pourquoi. Une salle nominative a été vérifiée à sa création.
                                        .thenCompose(_ -> room.origin() != RoomOrigin.SHARED
                                                        || playerId.equals(room.initiatorId())
                                                ? CompletableFuture.<Void>completedFuture(null)
                                                : userBlockService.requireNotBlocked(playerId, room.initiatorId()))
                                        .thenCompose(_ -> roomPlayer(playerId, room.topicId())))
                .thenCompose(
                        player ->
                                commandGateway
                                        .send(
                                                new RoomCommand.JoinRoomCommand(
                                                        roomId, playerId, player, presenceId))
                                        .thenAccept(_ -> {}));
    }

    /**
     * Snapshot de profil du joueur qui apparaît. Une progression absente ne bloque pas l'entrée
     * (repli XP de sujet 0), un profil introuvable, si.
     */
    private CompletableFuture<RoomPlayer> roomPlayer(String playerId, String topicId) {
        CompletableFuture<Profile> profileFuture = profileLookup.get(playerId);
        CompletableFuture<PlayerProgress> progressFuture =
                progressionLookup.get(playerId).exceptionally(_ -> null);
        CompletableFuture<TopicProgress> topicProgressFuture =
                progressionLookup.getTopic(playerId, topicId).exceptionally(_ -> null);
        return profileFuture
                .thenCombine(
                        progressFuture,
                        (profile, progress) -> new PlayerWithProgress(profile, progress))
                .thenCombine(
                        topicProgressFuture,
                        (player, topicProgress) ->
                                new RoomPlayer(
                                        player.profile().userId(),
                                        player.profile().pseudonym(),
                                        player.profile().language(),
                                        topicProgress == null ? 0 : Math.max(0, topicProgress.xp()),
                                        player.progress() == null
                                                ? null
                                                : player.progress().rating()));
    }

    private record PlayerWithProgress(Profile profile, PlayerProgress progress) {}

    /**
     * Le joueur n'est plus présent : constaté par {@code PresenceChannelListener} (session STOMP,
     * motif).
     */
    public CompletableFuture<Void> leave(
            String roomId, String playerId, String presenceId, RoomLeaveReason reason) {
        return requireRoom(roomId)
                .thenCompose(
                        _ ->
                                commandGateway
                                        .send(
                                                new RoomCommand.LeaveRoomCommand(
                                                        roomId, playerId, presenceId, reason))
                                        .thenAccept(_ -> {}));
    }

    public CompletableFuture<Void> cancel(String roomId, String playerId) {
        return send(roomId, new RoomCommand.CancelRoomCommand(roomId, playerId, RoomCancelReason.PLAYER_CANCELLED));
    }

    /** L'invité accepte le défi ; les invariants (invité seul, invitation en attente) vivent dans l'agrégat. */
    public CompletableFuture<Void> accept(String roomId, String playerId) {
        return send(roomId, new RoomCommand.AcceptRoomInvitationCommand(roomId, playerId));
    }

    public CompletableFuture<Void> decline(String roomId, String playerId) {
        return send(roomId, new RoomCommand.DeclineRoomInvitationCommand(roomId, playerId));
    }

    /**
     * Relance selon l'étape : le lanceur renvoie son invitation (10 min), ou un joueur présent appelle l'autre, absent
     * (10 s). Une relance refusée remonte le {@code Problem} métier de l'agrégat.
     */
    public CompletableFuture<Void> remind(String roomId, String playerId) {
        return send(roomId, new RoomCommand.RemindRoomCommand(roomId, playerId));
    }

    private CompletableFuture<Void> send(String roomId, RoomCommand command) {
        return requireRoom(roomId).thenCompose(_ -> commandGateway.send(command).thenAccept(_ -> {}));
    }

    /**
     * Pré-vérification : une salle purgée (état terminal + rétention) doit répondre 404, pas une
     * {@code AggregateDeletedException} non mappée par le SDK.
     */
    private CompletableFuture<Void> requireRoom(String roomId) {
        return queryGateway
                .query(new RoomQuery.GetRoomById(roomId), QueryResponseTypes.instanceOf(Room.class))
                .thenAccept(_ -> {});
    }

    private CompletableFuture<List<RoomView>> enrich(List<Room> rooms, String viewerId) {
        List<String> topicIds = rooms.stream().map(Room::topicId).distinct().toList();
        List<String> opponentIds =
                rooms.stream()
                        .map(room -> opponentIdOf(viewerId, room))
                        .filter(id -> id != null && !JoutioConstants.SYSTEM_USER_ID.equals(id))
                        .distinct()
                        .toList();

        CompletableFuture<List<Topic>> topicsFuture =
                topicIds.isEmpty()
                        ? CompletableFuture.completedFuture(List.of())
                        : queryGateway.query(
                                new TopicQuery.GetTopicsByIdsQuery(topicIds),
                                QueryResponseTypes.multipleInstancesOf(Topic.class));
        CompletableFuture<List<Profile>> profilesFuture =
                opponentIds.isEmpty()
                        ? CompletableFuture.completedFuture(List.of())
                        : profileLookup.getAll(opponentIds);
        CompletableFuture<Map<String, UserAccountStatus>> statusesFuture =
                identityLookup
                        .statuses(opponentIds)
                        .exceptionally(_ -> Map.<String, UserAccountStatus>of());
        CompletableFuture<List<PlayerProgress>> progressionsFuture =
                opponentIds.isEmpty()
                        ? CompletableFuture.completedFuture(List.of())
                        : queryGateway
                                .query(
                                        new ProgressionQuery.GetProgressionsByIdsQuery(opponentIds),
                                        QueryResponseTypes.multipleInstancesOf(
                                                PlayerProgress.class))
                                .exceptionally(_ -> List.of());

        return CompletableFuture.allOf(
                        topicsFuture, profilesFuture, statusesFuture, progressionsFuture)
                .thenApply(
                        _ -> {
                            Map<String, Topic> topicById =
                                    topicsFuture.join().stream()
                                            .collect(
                                                    Collectors.toMap(
                                                            Topic::topicId,
                                                            Function.identity(),
                                                            (first, _) -> first,
                                                            LinkedHashMap::new));
                            Map<String, Profile> profileById =
                                    profilesFuture.join().stream()
                                            .collect(
                                                    Collectors.toMap(
                                                            Profile::userId,
                                                            Function.identity(),
                                                            (first, _) -> first,
                                                            LinkedHashMap::new));
                            Map<String, UserAccountStatus> statusById = statusesFuture.join();
                            Map<String, PlayerProgress> progressById =
                                    progressionsFuture.join().stream()
                                            .collect(
                                                    Collectors.toMap(
                                                            PlayerProgress::userId,
                                                            Function.identity(),
                                                            (first, _) -> first,
                                                            LinkedHashMap::new));

                            return rooms.stream()
                                    .map(
                                            room ->
                                                    toView(
                                                            room,
                                                            viewerId,
                                                            topicById,
                                                            profileById,
                                                            statusById,
                                                            progressById))
                                    .toList();
                        });
    }

    private static RoomView toView(
            Room room,
            String viewerId,
            Map<String, Topic> topicById,
            Map<String, Profile> profileById,
            Map<String, UserAccountStatus> statusById,
            Map<String, PlayerProgress> progressById) {
        String opponentId = opponentIdOf(viewerId, room);
        Profile opponent = opponentId == null ? null : profileById.get(opponentId);
        UserAccountStatus status = opponentId == null ? null : statusById.get(opponentId);
        PlayerProgress progress = opponentId == null ? null : progressById.get(opponentId);
        TopicRefView topic = TopicViews.toRef(room.topicId(), topicById.get(room.topicId()));

        return new RoomView(
                room.roomId(),
                topic,
                room.status(),
                room.vsBot(),
                room.origin(),
                RoomAccess.of(room.origin()),
                viewerId.equals(room.initiatorId()),
                opponentRef(opponentId, opponent, status, progress),
                room.initiatorPresent(),
                room.participantPresent(),
                room.gameId(),
                room.sourceGameId(),
                room.createdAt(),
                room.expiresAt(),
                room.remindableAt(),
                room.updatedAt());
    }

    /** Le bot n'a pas de profil : référence synthétique stable (nom système, sans avatar). */
    private static RoomOpponentView opponentRef(
            String opponentId,
            Profile opponent,
            UserAccountStatus status,
            PlayerProgress progress) {
        if (opponent != null) {
            return new RoomOpponentView(
                    opponent.userId(),
                    opponent.pseudonym(),
                    opponent.avatarOptions(),
                    status != null && status.suspended(Instant.now()),
                    IdentityLookup.premium(status, Instant.now()),
                    progress == null ? null : progress.rating(),
                    opponent.country());
        }
        if (JoutioConstants.SYSTEM_USER_ID.equals(opponentId)) {
            return new RoomOpponentView(
                    JoutioConstants.SYSTEM_USER_ID, JoutioConstants.SYSTEM_USER_NAME, null, false);
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

    /** Événements de la salle, du plus ancien au plus récent : repli de l'écran à l'ouverture. */
    public CompletableFuture<List<EventEnvelope>> events(String roomId) {
        return queryGateway.query(new RoomQuery.GetRoomEventsQuery(roomId),
                QueryResponseTypes.multipleInstancesOf(EventEnvelope.class));
    }
}
