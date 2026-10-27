package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.response.ActiveGameView;
import com.joutio.bff.infrastructure.in.api.response.GameResultView;
import com.joutio.bff.infrastructure.in.api.response.TopicRefView;
import com.joutio.bff.infrastructure.in.api.response.UserRefView;
import com.joutio.game.domain.command.GameCommand;
import com.joutio.game.domain.event.GameEvent;
import com.joutio.game.domain.exception.GameExceptions;
import com.joutio.game.domain.model.Game;
import com.joutio.game.domain.model.GamePlayerType;
import com.joutio.game.domain.model.GameQuestionChoice;
import com.joutio.game.domain.model.GameResult;
import com.joutio.game.domain.query.GameQuery;
import com.joutio.identity.domain.model.UserAccountStatus;
import com.joutio.microservice.core.domain.model.notification.EventEnvelope;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.profile.domain.model.EloRules;
import com.joutio.profile.domain.model.GameXp;
import com.joutio.profile.domain.model.MasteryTier;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Duel : écritures (réponse, abandon, annulation) et parties en cours du joueur (reprise depuis
 * l'accueil). La création d'une partie (bot ou humain) passe exclusivement par la salle.
 */
@Service
public class GameViewService {

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;
    private final ProfileLookup profileLookup;
    private final ProgressionLookup progressionLookup;
    private final IdentityLookup identityLookup;

    public GameViewService(CommandGateway commandGateway,
                           QueryGateway queryGateway,
                           ProfileLookup profileLookup,
                           ProgressionLookup progressionLookup,
                           IdentityLookup identityLookup) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
        this.profileLookup = profileLookup;
        this.progressionLookup = progressionLookup;
        this.identityLookup = identityLookup;
    }

    /**
     * Parties en cours du joueur (bannière de reprise), les plus récentes d'abord. Collection
     * vide si aucune partie active : l'absence n'est pas une erreur (pas de 404).
     */
    public CompletableFuture<List<ActiveGameView>> activeGames(String userId) {
        return queryGateway
                .query(new GameQuery.GetActiveGamesQuery(userId),
                        QueryResponseTypes.multipleInstancesOf(Game.class))
                .thenCompose(games -> enrich(userId, games));
    }

    private CompletableFuture<List<ActiveGameView>> enrich(String userId, List<Game> games) {
        if (games.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<String> topicIds = games.stream().map(Game::topicId).distinct().toList();
        List<String> opponentIds = games.stream()
                .map(game -> opponentIdOf(userId, game))
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        CompletableFuture<List<Topic>> topicsFuture = queryGateway.query(
                new TopicQuery.GetTopicsByIdsQuery(topicIds),
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

            return games.stream()
                    .map(game -> toActiveView(game, userId, topicById, profileById, statusById))
                    .toList();
        });
    }

    private static ActiveGameView toActiveView(Game game,
                                               String userId,
                                               Map<String, Topic> topicById,
                                               Map<String, Profile> profileById,
                                               Map<String, UserAccountStatus> statusById) {
        String opponentId = opponentIdOf(userId, game);
        Profile opponent = opponentId == null ? null : profileById.get(opponentId);
        UserAccountStatus status = opponentId == null ? null : statusById.get(opponentId);
        TopicRefView topic = TopicViews.toRef(game.topicId(), topicById.get(game.topicId()));
        UserRefView opponentRef = opponent == null
                ? null
                : new UserRefView(
                        opponent.userId(),
                        opponent.pseudonym(),
                        opponent.avatarOptions(),
                        status != null && status.suspended(Instant.now()));
        return new ActiveGameView(
                game.gameId(), topic, opponentRef, game.opponent(), game.status(), game.createdAt());
    }

    private static String opponentIdOf(String userId, Game game) {
        if (GamePlayerType.BOT.equals(game.opponent())) {
            return null;
        }
        return userId.equals(game.player1Id()) ? game.player2Id() : game.player1Id();
    }

    public CompletableFuture<Void> answer(String gameId, String playerId, GameQuestionChoice choice) {
        return commandGateway
                .send(new GameCommand.AnswerQuestionCommand(gameId, playerId, choice, Instant.now()))
                .thenAccept(_ -> {
                });
    }

    public CompletableFuture<Void> abandon(String gameId, String playerId) {
        return commandGateway
                .send(new GameCommand.ForfeitGameCommand(gameId, playerId))
                .thenAccept(_ -> {
                });
    }

    public CompletableFuture<Void> cancel(String gameId) {
        return commandGateway
                .send(new GameCommand.CancelGameCommand(gameId, "PLAYER_CANCELLED"))
                .thenAccept(_ -> {
                });
    }

    /**
     * Résultat détaillé d'une partie terminée pour le joueur courant, enrichi de la récompense
     * XP de cette partie ({@code null} tant que la projection n'est pas disponible), de l'Elo
     * <b>à l'instant de la partie</b> (snapshot porté par l'agrégat game + variation du duel) et
     * de la maîtrise du sujet après le duel. Le résultat est **privé** : la partie doit exister
     * et le lecteur doit en être l'un des deux joueurs (403 sinon).
     */
    public CompletableFuture<GameResultView> result(String gameId, String playerId) {
        return requireParticipant(gameId, playerId)
                .thenCompose(_ -> composeResult(gameId, playerId));
    }

    /**
     * Historique d'événements d'une partie — réservé à ses deux joueurs (REST et revue).
     *
     * <p>L'autorisation est dérivée du <b>flux d'événements</b> lui-même, et non de la projection
     * {@code game} écrite de façon asynchrone : la salle redirige l'arène dès {@code GAME_CREATED},
     * et une lecture de projection encore en retard produisait un 404 transitoire (« Partie
     * introuvable ») alors que la partie existait. Le premier événement, {@code GameCreatedEvent},
     * porte les deux participants et suffit à autoriser la lecture.</p>
     */
    public CompletableFuture<List<EventEnvelope>> events(String gameId, String playerId) {
        return queryGateway.query(
                        new GameQuery.GetGameEventsQuery(gameId),
                        QueryResponseTypes.multipleInstancesOf(EventEnvelope.class))
                .thenCompose(envelopes -> {
                    Optional<GameEvent.GameCreatedEvent> created = envelopes.stream()
                            .map(EventEnvelope::payload)
                            .filter(GameEvent.GameCreatedEvent.class::isInstance)
                            .map(GameEvent.GameCreatedEvent.class::cast)
                            .findFirst();
                    if (created.isEmpty()) {
                        return CompletableFuture.failedFuture(
                                new GameExceptions.GameNotFoundProblem(gameId));
                    }
                    GameEvent.GameCreatedEvent game = created.get();
                    boolean participant = playerId.equals(game.player1Id())
                            || playerId.equals(game.player2Id());
                    return participant
                            ? CompletableFuture.completedFuture(envelopes)
                            : CompletableFuture.failedFuture(
                                    new BffProblems.GameAccessDeniedProblem(gameId));
                });
    }

    /**
     * Vérifie que le lecteur est l'un des deux joueurs. La partie inexistante remonte le
     * {@code GameNotFoundProblem} du service game (404) ; un tiers reçoit un 403 BFF explicite.
     */
    private CompletableFuture<Game> requireParticipant(String gameId, String playerId) {
        return queryGateway.query(
                        new GameQuery.GetGameByIdQuery(gameId),
                        QueryResponseTypes.instanceOf(Game.class))
                .thenCompose(game -> {
                    boolean participant = playerId.equals(game.player1Id())
                            || playerId.equals(game.player2Id());
                    return participant
                            ? CompletableFuture.completedFuture(game)
                            : CompletableFuture.failedFuture(
                                    new BffProblems.GameAccessDeniedProblem(gameId));
                });
    }

    private CompletableFuture<GameResultView> composeResult(String gameId, String playerId) {
        CompletableFuture<GameResult> resultFuture = queryGateway.query(
                new GameQuery.GetGameResultQuery(gameId, playerId),
                QueryResponseTypes.instanceOf(GameResult.class));
        CompletableFuture<List<GameXp>> xpFuture = queryGateway.query(
                new ProgressionQuery.GetGamesXpQuery(playerId, List.of(gameId)),
                QueryResponseTypes.multipleInstancesOf(GameXp.class));


        return resultFuture.thenCompose(gameResult -> {
            CompletableFuture<List<Topic>> topicsFuture = queryGateway.query(
                    new TopicQuery.GetTopicsByIdsQuery(List.of(gameResult.topicId())),
                    QueryResponseTypes.multipleInstancesOf(Topic.class));
            CompletableFuture<Profile> opponentFuture = gameResult.botGame() || gameResult.opponentId() == null
                    ? CompletableFuture.completedFuture(null)
                    : profileLookup.get(gameResult.opponentId()).exceptionally(_ -> null);
            CompletableFuture<UserAccountStatus> opponentStatusFuture =
                    gameResult.botGame() || gameResult.opponentId() == null
                            ? CompletableFuture.completedFuture(null)
                            : identityLookup.status(gameResult.opponentId()).exceptionally(_ -> null);
            CompletableFuture<PlayerProgress> playerProgressFuture = progressionLookup.get(playerId)
                    .exceptionally(_ -> null);
            CompletableFuture<TopicProgress> topicProgressFuture = progressionLookup
                    .getTopic(playerId, gameResult.topicId())
                    .exceptionally(_ -> null);
            CompletableFuture<PlayerProgress> opponentProgressFuture =
                    gameResult.botGame() || gameResult.opponentId() == null
                            ? CompletableFuture.completedFuture(null)
                            : progressionLookup.get(gameResult.opponentId()).exceptionally(_ -> null);
            CompletableFuture<List<GameXp>> opponentXpFuture =
                    gameResult.botGame() || gameResult.opponentId() == null
                            ? CompletableFuture.completedFuture(List.of())
                            : queryGateway.query(
                                    new ProgressionQuery.GetGamesXpQuery(gameResult.opponentId(), List.of(gameId)),
                                    QueryResponseTypes.multipleInstancesOf(GameXp.class));

            return CompletableFuture.allOf(topicsFuture, opponentFuture, xpFuture, opponentXpFuture,
                            opponentStatusFuture, playerProgressFuture, topicProgressFuture,
                            opponentProgressFuture).thenApply(_ -> {
                GameXp gameXp = xpFuture.join().stream().findFirst().orElse(null);
                Integer xp = gameXp == null ? null : gameXp.xp();
                int eloDelta = gameXp == null ? 0 : gameXp.ratingDelta();
                boolean rated = gameXp != null && gameXp.rated();
                GameXp opponentGameXp = opponentXpFuture.join().stream().findFirst().orElse(null);
                int opponentEloDelta = opponentGameXp == null ? 0 : opponentGameXp.ratingDelta();
                boolean opponentRated = opponentGameXp != null && opponentGameXp.rated();
                // Entraînement (duel bot) : XP nulle par construction, donc aucun bonus de victoire
                // à dériver — `xp - score` produirait un bonus négatif.
                GameResultView.RewardView reward = xp == null
                        ? null
                        : new GameResultView.RewardView(xp, gameResult.botGame() ? 0 : xp - gameResult.myScore());

                PlayerProgress playerProgress = playerProgressFuture.join();
                // Elo relatif à la partie : snapshot de création + variation du duel. Les anciens
                // événements sans snapshot retombent sur l'Elo courant (compatibilité).
                int rating = gameResult.myRating() != null
                        ? Math.max(0, gameResult.myRating() + eloDelta)
                        : playerProgress == null ? EloRules.INITIAL_RATING : playerProgress.rating();
                int peakRating = playerProgress == null ? rating : playerProgress.peakRating();
                int ratedGames = playerProgress == null ? 0 : playerProgress.ratedGames();
                boolean provisional = playerProgress == null || playerProgress.provisional();
                GameResultView.ProgressionResultView progression = new GameResultView.ProgressionResultView(
                        rating,
                        peakRating,
                        ratedGames,
                        provisional);

                int xpGained = xp == null ? 0 : xp;
                TopicProgress topicProgress = topicProgressFuture.join();
                int topicXp = topicProgress == null
                        ? xpGained
                        : Math.max(topicProgress.xp(), xpGained);
                MasteryTier tier = MasteryTier.tierFor(topicXp);
                GameResultView.TopicMasteryView topicMastery = new GameResultView.TopicMasteryView(
                        topicXp,
                        xpGained,
                        tier.name(),
                        tier.next() == null ? null : tier.next().name(),
                        tier.progressPercent(topicXp),
                        tier.remainingXp(topicXp));

                Topic topic = topicsFuture.join().stream().findFirst().orElse(null);
                Profile opponent = opponentFuture.join();
                PlayerProgress opponentProgress = opponentProgressFuture.join();
                int opponentRating = gameResult.opponentRating() != null
                        ? Math.max(0, gameResult.opponentRating() + opponentEloDelta)
                        : opponentProgress == null
                            ? EloRules.INITIAL_RATING
                            : opponentProgress.rating();
                UserAccountStatus opponentStatus = opponentStatusFuture.join();
                boolean opponentDeleted = opponentStatus != null && opponentStatus.deleted();
                UserRefView opponentRef = opponent == null
                        ? null
                        : new UserRefView(
                                opponent.userId(),
                                opponent.pseudonym(),
                                opponent.avatarOptions(),
                                opponentStatus != null && opponentStatus.suspended(Instant.now()));

                return new GameResultView(
                        gameResult.myScore(),
                        gameResult.opponentScore(),
                        gameResult.winnerId(),
                        gameResult.botGame(),
                        gameResult.basePoints(),
                        gameResult.speedBonus(),
                        gameResult.correctAnswers(),
                        gameResult.fastAnswers(),
                        gameResult.answeredRounds(),
                        gameResult.totalRounds(),
                        reward,
                        progression,
                        topicMastery,
                        eloDelta,
                        rated,
                        opponentRating,
                        opponentEloDelta,
                        opponentRated,
                        TopicViews.toRef(gameResult.topicId(), topic),
                        opponentRef,
                        gameResult.opponentId(),
                        gameResult.botDifficulty(),
                        opponentDeleted);
            });
        });
    }
}
