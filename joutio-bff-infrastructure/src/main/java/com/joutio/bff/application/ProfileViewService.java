package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.request.PeopleSort;
import com.joutio.bff.infrastructure.in.api.response.ActivityViewResponse;
import com.joutio.bff.infrastructure.in.api.response.BadgeCollectionView;
import com.joutio.bff.infrastructure.in.api.response.DuelStatsView;
import com.joutio.bff.infrastructure.in.api.response.GameHistoryItemView;
import com.joutio.bff.infrastructure.in.api.response.HeadToHeadView;
import com.joutio.bff.infrastructure.in.api.response.PageResponse;
import com.joutio.bff.infrastructure.in.api.response.PlayerCardView;
import com.joutio.bff.infrastructure.in.api.response.PlayerProfileView;
import com.joutio.bff.infrastructure.in.api.response.PresenceView;
import com.joutio.bff.infrastructure.in.api.response.ProgressionView;
import com.joutio.bff.infrastructure.in.api.response.TopicRefView;
import com.joutio.bff.infrastructure.in.api.response.TopicStatView;
import com.joutio.bff.infrastructure.in.api.response.TitlesView;
import com.joutio.bff.infrastructure.in.api.response.UserRefView;
import com.joutio.game.domain.model.Game;
import com.joutio.game.domain.model.GameHistoryOutcome;
import com.joutio.game.domain.model.GameHistorySort;
import com.joutio.game.domain.model.GamePlayerType;
import com.joutio.game.domain.model.GameStatus;
import com.joutio.game.domain.model.PlayerGamesPage;
import com.joutio.game.domain.query.GameQuery;
import com.joutio.identity.domain.model.UserAccountStatus;
import com.joutio.microservice.core.domain.constant.JoutioConstants;
import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.profile.domain.model.ActivityView;
import com.joutio.profile.domain.model.Badge;
import com.joutio.profile.domain.model.BadgeFamily;
import com.joutio.profile.domain.model.BadgeMetric;
import com.joutio.profile.domain.model.GameXp;
import com.joutio.profile.domain.model.MasteryTier;
import com.joutio.profile.domain.model.PlayerPresence;
import com.joutio.profile.domain.model.PlayerProgress;
import com.joutio.profile.domain.model.PlayerTitle;
import com.joutio.profile.domain.model.Profile;
import com.joutio.profile.domain.model.TopicProgress;
import com.joutio.profile.domain.query.ActivityQuery;
import com.joutio.profile.domain.query.PresenceQuery;
import com.joutio.profile.domain.query.ProgressionQuery;
import com.joutio.social.domain.model.FollowDirection;
import com.joutio.social.domain.model.UserFollowCounts;
import com.joutio.social.domain.model.UserFollower;
import com.joutio.social.domain.query.UserFollowerQuery;
import com.joutio.theme.domain.model.Topic;
import com.joutio.theme.domain.query.TopicQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Composition des vues joueur : profil public, listes de personnes, historique de duels,
 * bilan de confrontations, activité.
 */
@Service
public class ProfileViewService {

    private static final int MAX_PEOPLE_SCAN = FollowLookup.MAX_LIST_SIZE;
    private static final int HEAD_TO_HEAD_PAGE_SIZE = 200;
    private static final int HEAD_TO_HEAD_MAX_PAGES = 5;

    /** Fuseau des journées d'activité, aligné sur `joutio-profile` par défaut. */
    private ZoneId activityZone = ZoneId.of("Europe/Paris");

    @Value("${joutio.activity.zone:Europe/Paris}")
    void setActivityZone(String zone) {
        this.activityZone = ZoneId.of(zone);
    }

    private final QueryGateway queryGateway;
    private final ProfileLookup profileLookup;
    private final ProgressionLookup progressionLookup;
    private final FollowLookup followLookup;
    private final IdentityLookup identityLookup;

    public ProfileViewService(QueryGateway queryGateway,
                              ProfileLookup profileLookup,
                              ProgressionLookup progressionLookup,
                              FollowLookup followLookup,
                              IdentityLookup identityLookup) {
        this.queryGateway = queryGateway;
        this.profileLookup = profileLookup;
        this.progressionLookup = progressionLookup;
        this.followLookup = followLookup;
        this.identityLookup = identityLookup;
    }

    public CompletableFuture<PlayerProfileView> profileView(String viewerId, String userId) {
        // Compte système bot : identité minimale uniquement, aucune information de progression,
        // de statistiques ou de social n'est exposée.
        if (JoutioConstants.SYSTEM_USER_ID.equals(userId)) {
            return profileLookup.get(userId).thenApply(profile -> new PlayerProfileView(
                    profile.userId(),
                    profile.pseudonym(),
                    null,
                    null,
                    profile.avatarOptions(),
                    null,
                    null,
                    false,
                    false,
                    null,
                    new ProgressionView(List.of(),
                            com.joutio.profile.domain.model.EloRules.INITIAL_RATING,
                            com.joutio.profile.domain.model.EloRules.INITIAL_RATING,
                            0, true),
                    new DuelStatsView(0, 0, 0, 0, 0, 0, 0, 0),
                    0,
                    0,
                    false,
                    true));
        }
        CompletableFuture<Profile> profileFuture = profileLookup.get(userId);
        CompletableFuture<PlayerProgress> progressFuture = progressionLookup.get(userId);
        CompletableFuture<UserFollowCounts> countsFuture = followLookup.userCounts(userId);
        CompletableFuture<List<PlayerPresence>> presenceFuture = queryGateway.query(
                new PresenceQuery.GetPresencesByIdsQuery(List.of(userId)),
                QueryResponseTypes.multipleInstancesOf(PlayerPresence.class));
        CompletableFuture<Boolean> followingFuture = userId.equals(viewerId)
                ? CompletableFuture.completedFuture(false)
                : followLookup.userFollowed(viewerId, userId);
        CompletableFuture<UserAccountStatus> statusFuture = identityLookup.status(userId)
                .exceptionally(_ -> null);

        return CompletableFuture.allOf(profileFuture, progressFuture, countsFuture, presenceFuture, followingFuture,
                        statusFuture)
                .thenApply(_ -> {
                    Profile profile = profileFuture.join();
                    PlayerProgress progress = progressFuture.join();
                    UserFollowCounts counts = countsFuture.join();
                    PlayerPresence presence = presenceFuture.join().stream().findFirst().orElse(null);
                    UserAccountStatus status = statusFuture.join();
                    return new PlayerProfileView(
                            profile.userId(),
                            profile.pseudonym(),
                            profile.bio(),
                            profile.country(),
                            profile.avatarOptions(),
                            profile.bannerUrl(),
                            profile.equippedTitle(),
                            profile.userId().equals(viewerId),
                            followingFuture.join(),
                            presence == null ? null : toPresence(presence),
                            ProgressionViews.toView(progress),
                            ProgressionViews.toStats(progress),
                            counts.followers(),
                            counts.following(),
                            status != null && status.suspended(Instant.now()),
                            false);
                });
    }

    public CompletableFuture<Optional<PresenceView>> presence(String userId) {
        return queryGateway.query(
                        new PresenceQuery.GetPresencesByIdsQuery(List.of(userId)),
                        QueryResponseTypes.multipleInstancesOf(PlayerPresence.class))
                .thenApply(presences -> presences.stream().findFirst().map(ProfileViewService::toPresence));
    }

    /** Activité journalière et séries d'un joueur, sur la fenêtre demandée (calendrier). */
    public CompletableFuture<ActivityViewResponse> activity(String userId, LocalDate from, LocalDate to) {
        return queryGateway.query(
                        new ActivityQuery.GetActivityQuery(userId, from, to),
                        QueryResponseTypes.instanceOf(ActivityView.class))
                .thenApply(activity -> new ActivityViewResponse(
                        activity.userId(),
                        activity.currentStreak(),
                        activity.longestStreak(),
                        activity.lastActiveDate(),
                        activity.totalActiveDays(),
                        activity.days().stream()
                                .map(day -> new ActivityViewResponse.Day(day.date(), day.games()))
                                .toList()));
    }

    /**
     * Titres thématiques débloqués par un joueur : **tous les paliers atteints** de chaque sujet
     * avec **au moins une partie humaine terminée** (abandon inclus, entraînement bot exclu) — le
     * palier d'entrée Fer compris même à 0 XP —, plus la clé de son **titre porté**. Un joueur
     * peut équiper un palier dépassé (ex. Fer alors qu'il est Bronze) : la vue doit donc
     * exposer chaque palier atteint, pas seulement le palier courant. Le libellé est composé par le
     * client (palier traduit + nom du sujet dans sa langue) — le serveur ne fige pas de texte.
     */
    public CompletableFuture<TitlesView> titles(String userId) {
        CompletableFuture<Profile> profileFuture = profileLookup.get(userId);
        CompletableFuture<PlayerProgress> progressFuture = progressionLookup.get(userId);

        return CompletableFuture.allOf(profileFuture, progressFuture).thenCompose(_ -> {
            PlayerProgress progress = progressFuture.join();
            List<String> earnedTopicIds = progress.gamesByTopic().keySet().stream()
                    .sorted()
                    .toList();

            CompletableFuture<List<Topic>> topicsFuture = earnedTopicIds.isEmpty()
                    ? CompletableFuture.completedFuture(List.of())
                    : queryGateway.query(
                            new TopicQuery.GetTopicsByIdsQuery(earnedTopicIds),
                            QueryResponseTypes.multipleInstancesOf(Topic.class));

            return topicsFuture.thenApply(topics -> {
                Map<String, Topic> topicById = topics.stream().collect(Collectors.toMap(
                        Topic::topicId, Function.identity(), (left, right) -> left));
                List<TitlesView.TitleView> titles = earnedTopicIds.stream()
                        .flatMap(topicId -> {
                            int topicXp = progress.xpForTopic(topicId);
                            MasteryTier current = MasteryTier.tierFor(topicXp);
                            Topic topic = topicById.get(topicId);
                            return Arrays.stream(MasteryTier.values())
                                    .filter(tier -> tier.ordinal() <= current.ordinal())
                                    .map(tier -> toTitle(topicId, topicXp, tier, topic));
                        })
                        .sorted(Comparator
                                .comparing((TitlesView.TitleView title) -> MasteryTier.valueOf(title.tier()))
                                .reversed()
                                .thenComparing(title -> displayName(title.topic())))
                        .toList();

                return new TitlesView(profileFuture.join().equippedTitle(), titles);
            });
        });
    }

    /**
     * Collection de badges d'un joueur : **tout le catalogue** (paliers verrouillés compris)
     * groupé par famille, avec la progression de chaque compteur — le client affiche « 7/10 » et
     * l'objectif suivant sans requête supplémentaire.
     */
    public CompletableFuture<BadgeCollectionView> badges(String userId) {
        return progressionLookup.get(userId).thenApply(progress -> {
            List<BadgeCollectionView.FamilyView> families = Arrays.stream(BadgeFamily.values())
                    .map(family -> new BadgeCollectionView.FamilyView(
                            family.name(),
                            Badge.ofFamily(family).stream()
                                    .map(badge -> toBadgeItem(badge, progress))
                                    .toList()))
                    .toList();

            return new BadgeCollectionView(
                    progress.badges().size(), Badge.values().length, families);
        });
    }

    private BadgeCollectionView.BadgeItemView toBadgeItem(Badge badge, PlayerProgress progress) {
        boolean earned = progress.badges().contains(badge);
        int target = badge.target();
        int current = earned && badge.isOneShot()
                ? target
                : metricValue(badge.metric(), progress);

        return new BadgeCollectionView.BadgeItemView(
                badge.name(),
                badge.label(),
                badge.tier().name(),
                badge.metric().name(),
                target,
                earned,
                Math.min(current, target));
    }

    /** Valeur courante du compteur d'un badge, telle que la mesure le read model de progression. */
    private int metricValue(BadgeMetric metric, PlayerProgress progress) {
        return switch (metric) {
            case HUMAN_DUELS -> progress.gamesPlayed();
            case HUMAN_WINS -> progress.wins();
            case TOPIC_MASTERY -> progress.xpByTopic().values().stream()
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(0);
            case DISTINCT_TOPICS -> (int) progress.xpByTopic().values().stream()
                    .filter(xp -> xp > 0)
                    .count();
            case WIN_STREAK -> progress.bestWinStreak();
            case PERFECT_DUELS -> progress.perfectDuels();
            case FAST_ANSWERS -> progress.fastAnswersTotal();
            // La progression affichée est la série **courante** (elle retombe à zéro après un
            // jour manqué). Les paliers déjà acquis restent acquis, mais la marche suivante
            // repart de la série effective, pas du record historique.
            case DAILY_STREAK -> effectiveCurrentDayStreak(progress);
            case FOLLOWERS_GAINED -> progress.followersGained();
            case CHALLENGES_SENT -> progress.challengesSent();
            case IMAGE_CORRECT -> progress.imageCorrectTotal();
            case EARNED_BADGES -> progress.badges().size();
            case ONE_SHOT -> 0;
        };
    }

    private int effectiveCurrentDayStreak(PlayerProgress progress) {
        if (progress.lastActiveDate() == null) {
            return 0;
        }
        LocalDate today = LocalDate.now(activityZone);
        if (progress.lastActiveDate().isBefore(today.minusDays(1))) {
            return 0;
        }
        return progress.currentDayStreak();
    }

    private static TitlesView.TitleView toTitle(String topicId, int topicXp,
                                                 MasteryTier tier, Topic topic) {
        return new TitlesView.TitleView(
                new PlayerTitle(topicId, tier).key(),
                topicId,
                tier.name(),
                tier.progressPercent(topicXp),
                tier.remainingXp(topicXp),
                tier.next() == null ? null : tier.next().name(),
                TopicViews.toRef(topicId, topic));
    }

    /** Nom d'affichage d'un sujet pour le tri serveur : FR d'abord (convention de repli du web). */
    private static String displayName(TopicRefView topic) {
        Map<Language, String> names = topic.names();
        if (names == null || names.isEmpty()) {
            return topic.topicId();
        }

        return Optional.ofNullable(names.get(Language.FR))
                .orElseGet(() -> names.values().iterator().next());
    }

    /**
     * Liste des abonnements/abonnés enrichie (profil, Elo, présence, suivi du lecteur).
     * Le périmètre est borné côté service social ({@value FollowLookup#MAX_LIST_SIZE}), puis
     * filtré/trié/paginé ici.
     */

    /**
     * Elo minimal en lot pour les bordures d'avatar : une seule query pour tous les joueurs
     * visibles, sans fan-out N+1.
     */
    public CompletableFuture<List<com.joutio.bff.infrastructure.in.api.response.PlayerRatingView>> ratings(
            List<String> userIds) {
        List<String> ids = userIds.stream().filter(Objects::nonNull).distinct().limit(100).toList();
        if (ids.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return queryGateway.query(new ProgressionQuery.GetProgressionsByIdsQuery(ids),
                        QueryResponseTypes.multipleInstancesOf(PlayerProgress.class))
                .thenApply(progressions -> progressions.stream()
                        .map(progress -> new com.joutio.bff.infrastructure.in.api.response.PlayerRatingView(
                                progress.userId(), progress.rating(), progress.provisional()))
                        .toList());
    }

    public CompletableFuture<PageResponse<PlayerCardView>> people(String viewerId,
                                                                  String userId,
                                                                  FollowDirection direction,
                                                                  String query,
                                                                  PeopleSort sort,
                                                                  int page,
                                                                  int size) {
        CompletableFuture<List<UserFollower>> followsFuture = queryGateway.query(
                new UserFollowerQuery.GetUserFollowsQuery(userId, direction, MAX_PEOPLE_SCAN),
                QueryResponseTypes.multipleInstancesOf(UserFollower.class));
        CompletableFuture<List<String>> viewerFollowingFuture = followLookup.followingIds(viewerId, MAX_PEOPLE_SCAN);

        return followsFuture.thenCompose(follows -> {
            List<String> ids = follows.stream()
                    .map(follow -> direction == FollowDirection.FOLLOWING ? follow.followedId() : follow.followerId())
                    .distinct()
                    .toList();

            CompletableFuture<List<Profile>> profilesFuture = profileLookup.getAll(ids);
            CompletableFuture<List<PlayerProgress>> progressionsFuture = ids.isEmpty()
                    ? CompletableFuture.completedFuture(List.of())
                    : queryGateway.query(new ProgressionQuery.GetProgressionsByIdsQuery(ids),
                            QueryResponseTypes.multipleInstancesOf(PlayerProgress.class));
            CompletableFuture<List<PlayerPresence>> presencesFuture = ids.isEmpty()
                    ? CompletableFuture.completedFuture(List.of())
                    : queryGateway.query(new PresenceQuery.GetPresencesByIdsQuery(ids),
                            QueryResponseTypes.multipleInstancesOf(PlayerPresence.class));
            CompletableFuture<Map<String, UserAccountStatus>> statusesFuture = identityLookup.statuses(ids)
                    .exceptionally(_ -> Map.<String, UserAccountStatus>of());

            return CompletableFuture.allOf(profilesFuture, progressionsFuture, presencesFuture, viewerFollowingFuture,
                            statusesFuture)
                    .thenApply(_ -> {
                        Map<String, Profile> profileById = byKey(profilesFuture.join(), Profile::userId);
                        Map<String, PlayerProgress> progressById = byKey(progressionsFuture.join(), PlayerProgress::userId);
                        Map<String, PlayerPresence> presenceById = byKey(presencesFuture.join(), PlayerPresence::userId);
                        Set<String> viewerFollowing = Set.copyOf(viewerFollowingFuture.join());
                        Map<String, UserAccountStatus> statusById = statusesFuture.join();
                        String normalizedQuery = query == null || query.isBlank() ? null : normalize(query);

                        List<PlayerCardView> cards = ids.stream()
                                .map(id -> toCard(id, profileById, progressById, presenceById, viewerFollowing,
                                        statusById))
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .filter(card -> normalizedQuery == null
                                        || normalize(card.pseudonym()).contains(normalizedQuery))
                                .sorted(peopleComparator(sort))
                                .toList();

                        return PageResponse.of(slice(cards, page, size), page, size, cards.size());
                    });
        });
    }

    /** Historique paginé des duels d'un joueur, enrichi adversaire + sujet + XP réelle. */
    public CompletableFuture<PageResponse<GameHistoryItemView>> games(String userId,
                                                                      String topicId,
                                                                      String opponentId,
                                                                      GamePlayerType opponentType,
                                                                      GameHistoryOutcome outcome,
                                                                      GameHistorySort sort,
                                                                      int page,
                                                                      int size) {
        return queryGateway.query(
                        new GameQuery.GetPlayerGamesQuery(userId, topicId, opponentId, opponentType,
                                outcome, sort, page, size),
                        QueryResponseTypes.instanceOf(PlayerGamesPage.class))
                .thenCompose(gamesPage -> enrichGames(userId, gamesPage.games())
                        .thenApply(items -> PageResponse.of(items, gamesPage.page(), gamesPage.size(),
                                gamesPage.totalElements())));
    }

    /** Bilan des duels communs entre deux joueurs (borné à 1000 parties). */
    public CompletableFuture<HeadToHeadView> headToHead(String userId, String against) {
        return allGamesAgainst(userId, against, 0, new ArrayList<>())
                .thenApply(games -> {
                    int wins = 0;
                    int losses = 0;
                    int draws = 0;
                    for (Game game : games) {
                        if (game.winnerId() == null) {
                            draws++;
                        } else if (game.winnerId().equals(userId)) {
                            wins++;
                        } else {
                            losses++;
                        }
                    }
                    return new HeadToHeadView(games.size(), wins, losses, draws);
                });
    }

    /**
     * Répartition des parties humaines par sujet (donut « Sujets joués » du profil) : progression
     * par thème (parties, XP) enrichie des informations éditoriales du sujet. Les sujets
     * introuvables (supprimés) sont ignorés.
     */
    public CompletableFuture<List<TopicStatView>> topics(String userId) {
        return queryGateway.query(
                        new ProgressionQuery.GetTopicProgressionsQuery(userId),
                        QueryResponseTypes.multipleInstancesOf(TopicProgress.class))
                .thenCompose(progressions -> {
                    List<String> topicIds = progressions.stream().map(TopicProgress::topicId).toList();
                    CompletableFuture<List<Topic>> topicsFuture = topicIds.isEmpty()
                            ? CompletableFuture.completedFuture(List.of())
                            : queryGateway.query(new TopicQuery.GetTopicsByIdsQuery(topicIds),
                                    QueryResponseTypes.multipleInstancesOf(Topic.class));
                    return topicsFuture.thenApply(topics -> {
                        Map<String, Topic> topicById = byKey(topics, Topic::topicId);
                        return progressions.stream()
                                .filter(progress -> topicById.containsKey(progress.topicId()))
                                .map(progress -> {
                                    Topic topic = topicById.get(progress.topicId());
                                    return new TopicStatView(topic.topicId(), topic.names(), topic.emoji(),
                                            topic.color(), topic.imageUrl(),
                                            progress.games(), progress.xp());
                                })
                                .toList();
                    });
                });
    }

    private CompletableFuture<List<GameHistoryItemView>> enrichGames(String userId, List<Game> games) {
        List<String> gameIds = games.stream().map(Game::gameId).toList();
        List<String> topicIds = games.stream().map(Game::topicId).distinct().toList();
        List<String> opponentIds = games.stream()
                .map(game -> opponentIdOf(game, userId))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        CompletableFuture<List<Topic>> topicsFuture = topicIds.isEmpty()
                ? CompletableFuture.completedFuture(List.of())
                : queryGateway.query(new TopicQuery.GetTopicsByIdsQuery(topicIds),
                        QueryResponseTypes.multipleInstancesOf(Topic.class));
        CompletableFuture<List<Profile>> opponentsFuture = opponentIds.isEmpty()
                ? CompletableFuture.completedFuture(List.of())
                : profileLookup.getAll(opponentIds);
        CompletableFuture<List<GameXp>> xpFuture = gameIds.isEmpty()
                ? CompletableFuture.completedFuture(List.of())
                : queryGateway.query(new ProgressionQuery.GetGamesXpQuery(userId, gameIds),
                        QueryResponseTypes.multipleInstancesOf(GameXp.class));
        CompletableFuture<Map<String, UserAccountStatus>> statusesFuture = identityLookup.statuses(opponentIds)
                .exceptionally(_ -> Map.<String, UserAccountStatus>of());

        return CompletableFuture.allOf(topicsFuture, opponentsFuture, xpFuture, statusesFuture)
                .thenApply(_ -> {
                    Map<String, Topic> topicById = byKey(topicsFuture.join(), Topic::topicId);
                    Map<String, Profile> opponentById = byKey(opponentsFuture.join(), Profile::userId);
                    Map<String, GameXp> xpByGame = byKey(xpFuture.join(), GameXp::gameId);
                    Map<String, UserAccountStatus> statusById = statusesFuture.join();
                    return games.stream()
                            .map(game -> toHistoryItem(userId, game, topicById, opponentById, xpByGame, statusById))
                            .toList();
                });
    }

    private CompletableFuture<List<Game>> allGamesAgainst(String userId,
                                                          String against,
                                                          int page,
                                                          List<Game> accumulator) {
        return queryGateway.query(
                        new GameQuery.GetPlayerGamesQuery(userId, null, against, null, null,
                                GameHistorySort.RECENT, page, HEAD_TO_HEAD_PAGE_SIZE),
                        QueryResponseTypes.instanceOf(PlayerGamesPage.class))
                .thenCompose(gamesPage -> {
                    accumulator.addAll(gamesPage.games());
                    boolean morePages = page + 1 < gamesPage.totalPages() && page + 1 < HEAD_TO_HEAD_MAX_PAGES;
                    return morePages
                            ? allGamesAgainst(userId, against, page + 1, accumulator)
                            : CompletableFuture.completedFuture(accumulator);
                });
    }

    private static Optional<PlayerCardView> toCard(String userId,
                                                   Map<String, Profile> profileById,
                                                   Map<String, PlayerProgress> progressById,
                                                   Map<String, PlayerPresence> presenceById,
                                                   Set<String> viewerFollowing,
                                                   Map<String, UserAccountStatus> statusById) {
        Profile profile = profileById.get(userId);
        if (profile == null) {
            return Optional.empty();
        }
        ProgressionView progression = ProgressionViews.toView(
                progressById.getOrDefault(userId, PlayerProgress.empty(userId)));
        PlayerPresence presence = presenceById.get(userId);
        UserAccountStatus status = statusById.get(userId);
        return Optional.of(new PlayerCardView(
                profile.userId(),
                profile.pseudonym(),
                profile.avatarOptions(),
                progression.rating(),
                progression.provisional(),
                viewerFollowing.contains(userId),
                presence == null ? null : toPresence(presence),
                status != null && status.suspended(Instant.now())));
    }

    private static Comparator<PlayerCardView> peopleComparator(PeopleSort sort) {
        if (sort == PeopleSort.ELO) {
            return Comparator.comparingInt(PlayerCardView::rating).reversed()
                    .thenComparing(PlayerCardView::pseudonym, String.CASE_INSENSITIVE_ORDER);
        }
        if (sort == PeopleSort.ALPHA) {
            return Comparator.comparing(PlayerCardView::pseudonym, String.CASE_INSENSITIVE_ORDER);
        }
        return (first, second) -> 0;
    }

    private static GameHistoryItemView toHistoryItem(String userId,
                                                     Game game,
                                                     Map<String, Topic> topicById,
                                                     Map<String, Profile> opponentById,
                                                     Map<String, GameXp> xpByGame,
                                                     Map<String, UserAccountStatus> statusById) {
        boolean isPlayer1 = game.player1Id().equals(userId);
        String opponentId = opponentIdOf(game, userId);
        Profile opponent = opponentId == null ? null : opponentById.get(opponentId);
        Topic topic = topicById.get(game.topicId());
        GameXp xp = xpByGame.get(game.gameId());
        UserAccountStatus status = opponentId == null ? null : statusById.get(opponentId);
        return new GameHistoryItemView(
                game.gameId(),
                TopicViews.toRef(game.topicId(), topic),
                opponent == null ? null : new UserRefView(
                        opponent.userId(),
                        opponent.pseudonym(),
                        opponent.avatarOptions(),
                        status != null && status.suspended(Instant.now())),
                game.opponent() == null ? null : game.opponent().name(),
                outcomeOf(game, userId),
                isPlayer1 ? game.player1Score() : game.player2Score(),
                isPlayer1 ? game.player2Score() : game.player1Score(),
                xp == null ? null : xp.xp(),
                game.endedAt() != null ? game.endedAt() : game.createdAt());
    }

    private static GameHistoryItemView.Outcome outcomeOf(Game game, String userId) {
        return switch (game.status()) {
            case IN_PROGRESS -> GameHistoryItemView.Outcome.IN_PROGRESS;
            case CANCELED -> GameHistoryItemView.Outcome.CANCELLED;
            case FINISHED -> finishedOutcome(game, userId);
        };
    }

    private static GameHistoryItemView.Outcome finishedOutcome(Game game, String userId) {
        if (game.winnerId() == null) {
            return GameHistoryItemView.Outcome.DRAW;
        }
        return game.winnerId().equals(userId)
                ? GameHistoryItemView.Outcome.WIN
                : GameHistoryItemView.Outcome.LOSS;
    }

    private static String opponentIdOf(Game game, String userId) {
        if (game.player1Id().equals(userId)) {
            return game.player2Id();
        }
        return game.player1Id();
    }

    private static PresenceView toPresence(PlayerPresence presence) {
        return new PresenceView(presence.userId(), presence.status(), presence.lastSeenAt());
    }

    private static <T> Map<String, T> byKey(List<T> items, Function<T, String> key) {
        return items.stream().collect(Collectors.toMap(key, Function.identity(), (first, _) -> first, LinkedHashMap::new));
    }

    private static <T> List<T> slice(List<T> items, int page, int size) {
        int from = Math.min(page * size, items.size());
        int to = Math.min(from + size, items.size());
        return items.subList(from, to);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase()
                .trim();
    }
}
