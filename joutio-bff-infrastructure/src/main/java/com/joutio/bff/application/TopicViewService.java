package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.request.LeaderboardPeriod;
import com.joutio.bff.infrastructure.in.api.request.LeaderboardScope;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.response.PageResponse;
import com.joutio.bff.infrastructure.in.api.response.TopicCardView;
import com.joutio.bff.infrastructure.in.api.response.TopicCategoryView;
import com.joutio.bff.infrastructure.in.api.response.TopicFacetsView;
import com.joutio.bff.infrastructure.in.api.response.TopicLeaderboardView;
import com.joutio.bff.infrastructure.in.api.response.TopicOverviewView;
import com.joutio.game.domain.query.GameQuery;
import com.joutio.identity.domain.model.UserAccountStatus;
import com.joutio.leaderboard.domain.model.LeaderboardPage;
import com.joutio.leaderboard.domain.model.LeaderboardRank;
import com.joutio.leaderboard.domain.model.LeaderboardRules;
import com.joutio.leaderboard.domain.model.TopicLeaderboardEntry;
import com.joutio.leaderboard.domain.query.LeaderboardQuery;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.profile.domain.model.MasteryTier;
import com.joutio.profile.domain.model.TopicProgress;
import com.joutio.profile.domain.model.PlayerProgress;
import com.joutio.profile.domain.query.ProgressionQuery;
import com.joutio.theme.domain.model.QuestionStatus;
import com.joutio.theme.domain.model.Topic;
import com.joutio.theme.domain.model.TopicCategory;
import com.joutio.theme.domain.model.TopicFacetCount;
import com.joutio.theme.domain.model.TopicPage;
import com.joutio.theme.domain.model.TopicSort;
import com.joutio.theme.domain.query.TopicQuery;
import com.joutio.theme.domain.util.SearchText;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Composition des vues sujets : catalogue, facettes, fiche agrégée, classement.
 */
@Service
public class TopicViewService {

    private final QueryGateway queryGateway;
    private final FollowLookup followLookup;
    private final LeaderboardScopeService leaderboardScopeService;
    private final IdentityLookup identityLookup;

    public TopicViewService(QueryGateway queryGateway,
                            FollowLookup followLookup,
                            LeaderboardScopeService leaderboardScopeService,
                            IdentityLookup identityLookup) {
        this.queryGateway = queryGateway;
        this.followLookup = followLookup;
        this.leaderboardScopeService = leaderboardScopeService;
        this.identityLookup = identityLookup;
    }

    public List<TopicCategoryView> categories() {
        return List.of(TopicCategory.values()).stream()
                .map(category -> new TopicCategoryView(category.name(), category.label()))
                .toList();
    }

    public CompletableFuture<PageResponse<TopicCardView>> list(String viewerId,
                                                               String query,
                                                               TopicCategory category,
                                                               boolean followedOnly,
                                                               boolean mine,
                                                               TopicSort sort,
                                                               int page,
                                                               int size) {
        if (mine) {
            if (followedOnly || (query != null && !query.isBlank()) || category != null || sort != null) {
                throw new BffProblems.InvalidTopicListRequestProblem(
                        "mine=true est exclusif de followed, q, category et sort");
            }
            return myTopics(viewerId, page, size);
        }
        if (!followedOnly) {
            CompletableFuture<TopicPage> pageFuture = queryGateway.query(
                    new TopicQuery.GetTopicPageQuery(query, category, sort, page, size),
                    QueryResponseTypes.instanceOf(TopicPage.class));
            CompletableFuture<List<String>> followedFuture = followLookup.followedTopicIds(viewerId, FollowLookup.MAX_LIST_SIZE);
            return CompletableFuture.allOf(pageFuture, followedFuture)
                    .thenApply(_ -> {
                        Set<String> followed = Set.copyOf(followedFuture.join());
                        List<TopicCardView> cards = pageFuture.join().topics().stream()
                                .map(topic -> TopicViews.toCard(topic, followed.contains(topic.topicId())))
                                .toList();
                        TopicPage result = pageFuture.join();
                        return PageResponse.of(cards, result.page(), result.size(), result.totalElements());
                    });
        }

        return followLookup.followedTopicIds(viewerId, FollowLookup.MAX_LIST_SIZE)
                .thenCompose(followedIds -> queryGateway.query(
                                new TopicQuery.GetTopicsByIdsQuery(followedIds),
                                QueryResponseTypes.multipleInstancesOf(Topic.class))
                        .thenApply(topics -> {
                            String normalizedQuery = SearchText.normalize(query);
                            List<TopicCardView> cards = topics.stream()
                                    .filter(topic -> category == null || topic.category() == category)
                                    .filter(topic -> normalizedQuery == null || normalizedQuery.isBlank()
                                            || topic.names().values().stream()
                                                    .map(SearchText::normalize)
                                                    .anyMatch(candidate -> candidate.contains(normalizedQuery)))
                                    .sorted(topicComparator(sort))
                                    .map(topic -> TopicViews.toCard(topic, true))
                                    .toList();
                            return PageResponse.of(slice(cards, page, size), page, size, cards.size());
                        }));
    }

    private CompletableFuture<PageResponse<TopicCardView>> myTopics(String viewerId, int page, int size) {
        CompletableFuture<TopicPage> pageFuture = queryGateway.query(
                new TopicQuery.GetTopicsByCreatorQuery(viewerId, page, size),
                QueryResponseTypes.instanceOf(TopicPage.class));
        CompletableFuture<List<String>> followedFuture =
                followLookup.followedTopicIds(viewerId, FollowLookup.MAX_LIST_SIZE);

        return CompletableFuture.allOf(pageFuture, followedFuture).thenApply(_ -> {
            Set<String> followed = Set.copyOf(followedFuture.join());
            TopicPage result = pageFuture.join();
            List<TopicCardView> cards = result.topics().stream()
                    .map(topic -> TopicViews.toCard(topic, followed.contains(topic.topicId())))
                    .toList();
            return PageResponse.of(cards, result.page(), result.size(), result.totalElements());
        });
    }

    public CompletableFuture<TopicFacetsView> facets(String viewerId, String query, boolean followedOnly) {
        CompletableFuture<List<String>> topicIdsFuture = followedOnly
                ? followLookup.followedTopicIds(viewerId, FollowLookup.MAX_LIST_SIZE)
                : CompletableFuture.completedFuture(null);

        return topicIdsFuture.thenCompose(topicIds -> queryGateway.query(
                        new TopicQuery.TopicFacetsQuery(query, topicIds),
                        QueryResponseTypes.multipleInstancesOf(TopicFacetCount.class))
                .thenApply(counts -> {
                    Map<TopicCategory, Long> countByCategory = counts.stream()
                            .collect(Collectors.toMap(TopicFacetCount::category, TopicFacetCount::count,
                                    Long::sum, LinkedHashMap::new));
                    List<TopicFacetsView.CategoryFacetView> categories = List.of(TopicCategory.values()).stream()
                            .map(category -> new TopicFacetsView.CategoryFacetView(
                                    category.name(),
                                    category.label(),
                                    countByCategory.getOrDefault(category, 0L)))
                            .toList();
                    long total = countByCategory.values().stream().mapToLong(Long::longValue).sum();
                    return new TopicFacetsView(total, categories);
                }));
    }

    public CompletableFuture<TopicOverviewView> overview(String viewerId, String topicId) {
        CompletableFuture<Topic> topicFuture = queryGateway.query(
                new TopicQuery.GetTopicByIdQuery(topicId),
                QueryResponseTypes.instanceOf(Topic.class));
        CompletableFuture<Boolean> followedFuture = followLookup.topicFollowed(topicId, viewerId);
        CompletableFuture<Optional<LeaderboardRank>> rankFuture = queryGateway.query(
                new LeaderboardQuery.GetTopicRankQuery(topicId, viewerId, false,
                        LeaderboardRules.currentMonth(), null, null),
                QueryResponseTypes.optionalInstanceOf(LeaderboardRank.class));
        CompletableFuture<TopicProgress> progressFuture = queryGateway.query(
                new ProgressionQuery.GetTopicProgressionQuery(viewerId, topicId),
                QueryResponseTypes.instanceOf(TopicProgress.class));
        CompletableFuture<Integer> completedFuture = queryGateway.query(
                new GameQuery.GetTopicCompletionQuery(viewerId, topicId),
                QueryResponseTypes.instanceOf(Integer.class));

        return CompletableFuture.allOf(topicFuture, followedFuture, rankFuture, progressFuture, completedFuture)
                .thenApply(_ -> {
                    Topic topic = topicFuture.join();
                    TopicCardView card = TopicViews.toCard(topic, followedFuture.join());
                    TopicProgress topicProgress = progressFuture.join();
                    int xp = topicProgress.xp();
                    MasteryTier tier = MasteryTier.tierFor(xp);
                    int totalQuestions = card.questionsCount();
                    Integer answered = completedFuture.join();
                    int completedQuestions = Math.min(answered == null ? 0 : answered, totalQuestions);
                    int completionPercent = totalQuestions > 0
                            ? (int) Math.round(completedQuestions * 100.0 / totalQuestions)
                            : 0;
                    TopicOverviewView.MyProgressView progress = new TopicOverviewView.MyProgressView(
                            xp,
                            topicProgress.games(),
                            tier.name(),
                            tier.progressPercent(xp),
                            tier.remainingXp(xp),
                            tier.next() == null ? null : tier.next().name(),
                            completedQuestions,
                            totalQuestions,
                            completionPercent);
                    return new TopicOverviewView(
                            card,
                            topic.titles() == null ? Map.of() : topic.titles(),
                            rankFuture.join().map(LeaderboardRank::rank).orElse(null),
                            progress,
                            Objects.equals(topic.creatorId(), viewerId));
                });
    }

    public CompletableFuture<TopicLeaderboardView> leaderboard(String viewerId,
                                                               String topicId,
                                                               LeaderboardPeriod period,
                                                               String month,
                                                               LeaderboardScope scope,
                                                               int page,
                                                               int size) {
        boolean monthly = period == LeaderboardPeriod.MONTHLY;
        String resolvedMonth = monthly
                ? (month == null || month.isBlank() ? LeaderboardRules.currentMonth() : month)
                : null;

        return leaderboardScopeService.resolve(scope.name(), viewerId)
                .thenCompose(scopeFilter -> {
                    CompletableFuture<LeaderboardPage> pageFuture = queryGateway.query(
                            new LeaderboardQuery.TopByTopicQuery(topicId, monthly, resolvedMonth, page, size,
                                    scopeFilter.memberIds(), scopeFilter.country()),
                            QueryResponseTypes.instanceOf(LeaderboardPage.class));
                    CompletableFuture<Optional<LeaderboardRank>> meFuture = queryGateway.query(
                            new LeaderboardQuery.GetTopicRankQuery(topicId, viewerId, monthly, resolvedMonth,
                                    scopeFilter.memberIds(), scopeFilter.country()),
                            QueryResponseTypes.optionalInstanceOf(LeaderboardRank.class));

                    return CompletableFuture.allOf(pageFuture, meFuture).thenCompose(_ -> {
                        LeaderboardPage result = pageFuture.join();
                        List<String> userIds = new java.util.ArrayList<>();
                        result.entries().forEach(entry -> userIds.add(entry.userId()));
                        meFuture.join().ifPresent(rank -> userIds.add(rank.entry().userId()));
                        CompletableFuture<Map<String, UserAccountStatus>> statusesFuture =
                                identityLookup.statuses(userIds)
                                        .exceptionally(_ -> Map.<String, UserAccountStatus>of());
                        CompletableFuture<List<PlayerProgress>> ratingsFuture =
                                queryGateway.query(new ProgressionQuery.GetProgressionsByIdsQuery(userIds),
                                        QueryResponseTypes.multipleInstancesOf(PlayerProgress.class));
                        return CompletableFuture.allOf(statusesFuture, ratingsFuture).thenApply(_ -> {
                            Map<String, UserAccountStatus> statusById = statusesFuture.join();
                            Map<String, PlayerProgress> ratingById = ratingsFuture.join().stream()
                                    .collect(java.util.stream.Collectors.toMap(
                                            PlayerProgress::userId, progress -> progress, (a, _) -> a));
                            List<TopicLeaderboardView.EntryView> entries = java.util.stream.IntStream
                                    .range(0, result.entries().size())
                                    .mapToObj(index -> toEntryView(
                                            result.entries().get(index),
                                            page * size + index + 1,
                                            statusById, ratingById))
                                    .toList();
                            TopicLeaderboardView.EntryView meView = meFuture.join()
                                    .map(rank -> toEntryView(rank.entry(), rank.rank(), statusById, ratingById))
                                    .orElse(null);
                            return new TopicLeaderboardView(
                                    PageResponse.of(entries, result.page(), result.size(), result.totalElements()),
                                    meView);
                        });
                    });
                });
    }

    private static TopicLeaderboardView.EntryView toEntryView(TopicLeaderboardEntry entry,
                                                              int rank,
                                                              Map<String, UserAccountStatus> statusById,
                                                              Map<String, PlayerProgress> ratingById) {
        UserAccountStatus status = statusById.get(entry.userId());
        PlayerProgress rating = ratingById.get(entry.userId());
        return new TopicLeaderboardView.EntryView(
                rank,
                entry.userId(),
                entry.pseudonym(),
                entry.avatarOptions(),
                entry.country(),
                rating == null ? 200 : rating.rating(),
                rating == null || rating.provisional(),
                entry.totalXp(),
                entry.monthlyXp(),
                status != null && status.suspended(Instant.now()));
    }

    private static Comparator<Topic> topicComparator(TopicSort sort) {
        if (sort == TopicSort.ALPHA) {
            return Comparator.comparing(Topic::name, String.CASE_INSENSITIVE_ORDER);
        }
        return Comparator.comparingInt((Topic topic) -> topic.followersCounter() == null ? 0 : topic.followersCounter())
                .reversed()
                .thenComparing(Topic::name, String.CASE_INSENSITIVE_ORDER);
    }

    private static <T> List<T> slice(List<T> items, int page, int size) {
        int from = Math.min(page * size, items.size());
        int to = Math.min(from + size, items.size());
        return items.subList(from, to);
    }
}
