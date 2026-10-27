package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.request.CreateChallengeRequest;
import com.joutio.bff.infrastructure.in.api.response.ChallengeView;
import com.joutio.bff.infrastructure.in.api.response.TopicRefView;
import com.joutio.bff.infrastructure.in.api.response.UserRefView;
import com.joutio.game.domain.model.Game;
import com.joutio.game.domain.model.GamePlayerType;
import com.joutio.game.domain.query.GameQuery;
import com.joutio.matchmaking.domain.command.ChallengeCommand;
import com.joutio.matchmaking.domain.model.Challenge;
import com.joutio.identity.domain.model.UserAccountStatus;
import com.joutio.matchmaking.domain.query.ChallengeQuery;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.profile.domain.model.Profile;
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
 * Défi nominatif (intention asynchrone) : création, consultation, acceptation/refus/annulation.
 * La salle temps réel naît à l'acceptation (saga matchmaking).
 */
@Service
public class ChallengeViewService {

    private final QueryGateway queryGateway;
    private final CommandGateway commandGateway;
    private final ProfileLookup profileLookup;
    private final IdentityLookup identityLookup;
    private final DuelCreationGuard duelCreationGuard;

    public ChallengeViewService(QueryGateway queryGateway,
                                CommandGateway commandGateway,
                                ProfileLookup profileLookup,
                                IdentityLookup identityLookup,
                                DuelCreationGuard duelCreationGuard) {
        this.queryGateway = queryGateway;
        this.commandGateway = commandGateway;
        this.profileLookup = profileLookup;
        this.identityLookup = identityLookup;
        this.duelCreationGuard = duelCreationGuard;
    }

    public CompletableFuture<String> create(String challengerId, CreateChallengeRequest request) {
        String challengeId = UUID.randomUUID().toString();
        // L'adversaire doit exister (404 sinon, jamais de défi orphelin) et être actif ; un
        // compte supprimé ne peut plus recevoir de défi ni de revanche. On vérifie le statut
        // identity avant le profil, car le profil est déjà supprimé pour un compte DELETED.
        return identityLookup.status(request.opponentId())
                .exceptionally(_ -> null)
                .thenCompose(status -> {
                    UserAccountStatus account = status;
                    if (account != null && account.deleted()) {
                        throw new BffProblems.ChallengeOpponentDeletedProblem(request.opponentId());
                    }
                    if (account != null && account.suspended(Instant.now())) {
                        throw new BffProblems.ChallengeOpponentSuspendedProblem(request.opponentId());
                    }
                    return profileLookup.get(request.opponentId());
                })
                .thenCompose(_ -> validateRematch(challengerId, request))
                .thenCompose(_ -> duelCreationGuard.ensureChallengeAllowed(
                        challengerId, request.opponentId(), request.topicId()))
                .thenCompose(_ -> commandGateway.send(new ChallengeCommand.CreateChallengeCommand(
                        challengeId, request.topicId(), challengerId, request.opponentId(), request.gameId())))
                .thenApply(_ -> challengeId);
    }

    /**
     * Validation d'une revanche ({@code gameId} renseigné) : la partie d'origine existe (404 via le
     * problème game), le challenger en est l'un des joueurs, c'est un duel humain/humain et
     * {@code opponentId} correspond bien à l'autre joueur.
     */
    private CompletableFuture<Void> validateRematch(String challengerId, CreateChallengeRequest request) {
        if (request.gameId() == null || request.gameId().isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        return queryGateway
                .query(new GameQuery.GetGameByIdQuery(request.gameId()),
                        QueryResponseTypes.instanceOf(Game.class))
                .thenAccept(game -> {
                    boolean participated = challengerId.equals(game.player1Id())
                            || challengerId.equals(game.player2Id());
                    if (!participated) {
                        throw new BffProblems.RematchNotAllowedProblem(request.gameId(), challengerId);
                    }
                    if (GamePlayerType.BOT.equals(game.opponent())) {
                        throw new BffProblems.InvalidRematchProblem(
                                "A bot duel cannot be rematched with a challenge");
                    }
                    String opponentId = challengerId.equals(game.player1Id())
                            ? game.player2Id()
                            : game.player1Id();
                    if (!opponentId.equals(request.opponentId())) {
                        throw new BffProblems.InvalidRematchProblem(
                                "opponentId must be the other player of the source game");
                    }
                });
    }

    public CompletableFuture<ChallengeView> get(String challengeId) {
        return queryGateway
                .query(new ChallengeQuery.GetChallengeById(challengeId),
                        QueryResponseTypes.instanceOf(Challenge.class))
                .thenCompose(challenge -> enrich(List.of(challenge))
                        .thenApply(views -> views.getFirst()));
    }

    public CompletableFuture<List<ChallengeView>> mine(String playerId) {
        return queryGateway
                .query(new ChallengeQuery.GetMyChallenges(playerId),
                        QueryResponseTypes.multipleInstancesOf(Challenge.class))
                .thenCompose(this::enrich);
    }

    public CompletableFuture<Void> accept(String challengeId, String playerId) {
        return requireChallenge(challengeId)
                .thenCompose(_ -> commandGateway
                        .send(new ChallengeCommand.AcceptChallengeCommand(challengeId, playerId))
                        .thenAccept(_ -> {
                        }));
    }

    public CompletableFuture<Void> decline(String challengeId, String playerId) {
        return requireChallenge(challengeId)
                .thenCompose(_ -> commandGateway
                        .send(new ChallengeCommand.DeclineChallengeCommand(challengeId, playerId))
                        .thenAccept(_ -> {
                        }));
    }

    public CompletableFuture<Void> cancel(String challengeId, String playerId) {
        return requireChallenge(challengeId)
                .thenCompose(_ -> commandGateway
                        .send(new ChallengeCommand.CancelChallengeCommand(challengeId, playerId))
                        .thenAccept(_ -> {
                        }));
    }

    private CompletableFuture<Void> requireChallenge(String challengeId) {
        return queryGateway
                .query(new ChallengeQuery.GetChallengeById(challengeId),
                        QueryResponseTypes.instanceOf(Challenge.class))
                .thenAccept(_ -> {
                });
    }

    private CompletableFuture<List<ChallengeView>> enrich(List<Challenge> challenges) {
        List<String> topicIds = challenges.stream().map(Challenge::topicId).distinct().toList();
        List<String> playerIds = challenges.stream()
                .flatMap(challenge -> java.util.stream.Stream.of(
                        challenge.challengerId(), challenge.opponentId()))
                .distinct()
                .toList();

        CompletableFuture<List<Topic>> topicsFuture = topicIds.isEmpty()
                ? CompletableFuture.completedFuture(List.of())
                : queryGateway.query(new TopicQuery.GetTopicsByIdsQuery(topicIds),
                        QueryResponseTypes.multipleInstancesOf(Topic.class));
        CompletableFuture<List<Profile>> profilesFuture = playerIds.isEmpty()
                ? CompletableFuture.completedFuture(List.of())
                : profileLookup.getAll(playerIds);
        CompletableFuture<Map<String, UserAccountStatus>> statusesFuture = identityLookup.statuses(playerIds)
                .exceptionally(_ -> Map.<String, UserAccountStatus>of());

        return CompletableFuture.allOf(topicsFuture, profilesFuture, statusesFuture).thenApply(_ -> {
            Map<String, Topic> topicById = topicsFuture.join().stream()
                    .collect(Collectors.toMap(Topic::topicId, Function.identity(), (first, _) -> first, LinkedHashMap::new));
            Map<String, Profile> profileById = profilesFuture.join().stream()
                    .collect(Collectors.toMap(Profile::userId, Function.identity(), (first, _) -> first, LinkedHashMap::new));
            Map<String, UserAccountStatus> statusById = statusesFuture.join();

            return challenges.stream()
                    .map(challenge -> toView(challenge, topicById, profileById, statusById))
                    .toList();
        });
    }

    private static ChallengeView toView(Challenge challenge,
                                        Map<String, Topic> topicById,
                                        Map<String, Profile> profileById,
                                        Map<String, UserAccountStatus> statusById) {
        TopicRefView topic = TopicViews.toRef(challenge.topicId(), topicById.get(challenge.topicId()));
        return new ChallengeView(
                challenge.challengeId(),
                topic,
                toRef(profileById.get(challenge.challengerId()), statusById),
                toRef(profileById.get(challenge.opponentId()), statusById),
                challenge.status(),
                challenge.roomId(),
                challenge.gameId(),
                challenge.createdAt(),
                challenge.expiresAt());
    }

    private static UserRefView toRef(Profile profile, Map<String, UserAccountStatus> statusById) {
        if (profile == null) {
            return null;
        }
        UserAccountStatus status = statusById.get(profile.userId());
        return new UserRefView(
                profile.userId(),
                profile.pseudonym(),
                profile.avatarOptions(),
                status != null && status.suspended(Instant.now()));
    }
}
