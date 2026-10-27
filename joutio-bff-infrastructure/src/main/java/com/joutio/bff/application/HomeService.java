package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.response.HomeView;
import com.joutio.bff.infrastructure.in.api.response.TopicCardView;
import com.joutio.game.domain.model.TopicPopularity;
import com.joutio.game.domain.query.GameQuery;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.theme.domain.model.Topic;
import com.joutio.theme.domain.model.TopicCategory;
import com.joutio.theme.domain.model.TopicPage;
import com.joutio.theme.domain.model.TopicSort;
import com.joutio.theme.domain.query.TopicQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Composition de l'accueil : sélection personnalisée **« Pour toi »** (centres d'intérêt
 * persistés), sujets suivis (récents), sujets **les plus joués en ce moment** (24 h, toutes les
 * parties de la communauté — aucun filtre joueur) et sujets récemment publiés / mis à jour
 * (nouvelles questions). Le Top 10 « en ce moment » est complété par les sujets les plus joués
 * de tous les temps ({@link TopicSort#MOST_PLAYED}) quand moins de 10 sujets ont été joués dans
 * la fenêtre de 24 h : les récents gardent la tête, le relais all-time complète.
 */
@Service
public class HomeService {

    private static final int FOLLOWED_LIMIT = 10;
    private static final int TRENDING_LIMIT = 10;
    private static final int NEW_LIMIT = 10;
    private static final int RECOMMENDED_LIMIT = 10;
    private static final Duration TRENDING_WINDOW = Duration.ofHours(24);

    private final QueryGateway queryGateway;
    private final FollowLookup followLookup;
    private final ProfileLookup profileLookup;
    private final TopicRecommendationService topicRecommendationService;

    public HomeService(QueryGateway queryGateway,
                       FollowLookup followLookup,
                       ProfileLookup profileLookup,
                       TopicRecommendationService topicRecommendationService) {
        this.queryGateway = queryGateway;
        this.followLookup = followLookup;
        this.profileLookup = profileLookup;
        this.topicRecommendationService = topicRecommendationService;
    }

    public CompletableFuture<HomeView> home(String userId) {
        CompletableFuture<List<String>> followedIdsFuture =
                followLookup.followedTopicIds(userId, FollowLookup.MAX_LIST_SIZE);
        // Les intérêts sont un signal best-effort : un profil indisponible ne casse pas l'accueil.
        CompletableFuture<List<TopicCategory>> interestsFuture = profileLookup.get(userId)
                .thenApply(profile -> parseInterests(profile.interests()))
                .exceptionally(_ -> List.of());

        return CompletableFuture.allOf(followedIdsFuture, interestsFuture)
                .thenCompose(_ -> home(followedIdsFuture.join(), interestsFuture.join()));
    }

    private CompletableFuture<HomeView> home(List<String> followedIds,
                                             List<TopicCategory> interests) {
        List<String> recentFollowedIds = followedIds.stream().limit(FOLLOWED_LIMIT).toList();

        CompletableFuture<List<Topic>> followedTopicsFuture = recentFollowedIds.isEmpty()
                ? CompletableFuture.completedFuture(List.of())
                : queryGateway.query(new TopicQuery.GetTopicsByIdsQuery(recentFollowedIds),
                        QueryResponseTypes.multipleInstancesOf(Topic.class));
        CompletableFuture<List<TopicPopularity>> popularFuture = queryGateway.query(
                new GameQuery.GetPopularTopicsQuery(Instant.now().minus(TRENDING_WINDOW), TRENDING_LIMIT),
                QueryResponseTypes.multipleInstancesOf(TopicPopularity.class));
        CompletableFuture<TopicPage> newTopicsFuture = queryGateway.query(
                new TopicQuery.GetTopicPageQuery(null, null, TopicSort.RECENT, 0, NEW_LIMIT),
                QueryResponseTypes.instanceOf(TopicPage.class));

        return CompletableFuture.allOf(followedTopicsFuture, popularFuture, newTopicsFuture).thenCompose(_ -> {
            List<String> trendingIds = popularFuture.join().stream()
                    .map(TopicPopularity::topicId)
                    .toList();
            CompletableFuture<List<Topic>> trendingTopicsFuture = trendingIds.isEmpty()
                    ? CompletableFuture.completedFuture(List.of())
                    : queryGateway.query(new TopicQuery.GetTopicsByIdsQuery(trendingIds),
                            QueryResponseTypes.multipleInstancesOf(Topic.class));
            CompletableFuture<List<Topic>> mostPlayedFuture = trendingIds.size() >= TRENDING_LIMIT
                    ? CompletableFuture.completedFuture(List.of())
                    : queryGateway.query(
                            new TopicQuery.GetTopicPageQuery(null, null, TopicSort.MOST_PLAYED, 0, TRENDING_LIMIT),
                            QueryResponseTypes.instanceOf(TopicPage.class))
                    .thenApply(TopicPage::topics);

            Set<String> followed = Set.copyOf(followedIds);
            // « Pour toi » est un bonus : une erreur de recommandation ne casse jamais l'accueil.
            CompletableFuture<List<TopicCardView>> recommendedFuture = interests.isEmpty()
                    ? CompletableFuture.completedFuture(List.of())
                    : topicRecommendationService.recommend(interests, followed, RECOMMENDED_LIMIT)
                    .exceptionally(_ -> List.of());

            return CompletableFuture.allOf(trendingTopicsFuture, mostPlayedFuture, recommendedFuture)
                    .thenApply(_ -> {
                        List<TopicCardView> followedTopics = followedTopicsFuture.join().stream()
                                .map(topic -> TopicViews.toCard(topic, true))
                                .toList();
                        List<TopicCardView> trending = trendingTopics(
                                trendingTopicsFuture.join(), mostPlayedFuture.join(), followed);
                        List<TopicCardView> newTopics = newTopicsFuture.join().topics().stream()
                                .map(topic -> TopicViews.toCard(topic, followed.contains(topic.topicId())))
                                .toList();
                        return new HomeView(followedTopics, trending, newTopics, recommendedFuture.join());
                    });
        });
    }

    /** Codes d'intérêt persistés → enum thème, inconnus ignorés (robustesse aux anciens codes). */
    private static List<TopicCategory> parseInterests(List<String> interests) {
        if (interests == null || interests.isEmpty()) {
            return List.of();
        }
        return interests.stream()
                .filter(java.util.Objects::nonNull)
                .map(code -> {
                    try {
                        return TopicCategory.valueOf(code.trim().toUpperCase(java.util.Locale.ROOT));
                    } catch (IllegalArgumentException _) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(3)
                .toList();
    }

    /**
     * Top 10 : les sujets réellement joués dans les 24 h d'abord, puis le relais all-time
     * ({@link TopicSort#MOST_PLAYED}) pour atteindre {@link #TRENDING_LIMIT} sans doublon.
     * L'ordre d'entrée est conservé et la liste est bornée à 10.
     */
    private static List<TopicCardView> trendingTopics(
            List<Topic> recentlyPlayed,
            List<Topic> mostPlayed,
            Set<String> followed) {
        Map<String, Topic> ordered = new LinkedHashMap<>();
        recentlyPlayed.forEach(topic -> ordered.putIfAbsent(topic.topicId(), topic));
        mostPlayed.forEach(topic -> ordered.putIfAbsent(topic.topicId(), topic));
        return ordered.values().stream()
                .limit(TRENDING_LIMIT)
                .map(topic -> TopicViews.toCard(topic, followed.contains(topic.topicId())))
                .toList();
    }
}
