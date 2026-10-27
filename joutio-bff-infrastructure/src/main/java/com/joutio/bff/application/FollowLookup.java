package com.joutio.bff.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.social.domain.model.FollowDirection;
import com.joutio.social.domain.model.TopicFollower;
import com.joutio.social.domain.model.UserFollowCounts;
import com.joutio.social.domain.model.UserFollower;
import com.joutio.social.domain.query.TopicFollowerQuery;
import com.joutio.social.domain.query.UserFollowerQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Lecture centralisée des follows (sujets et joueurs) pour les compositions BFF.
 * Les listes sont bornées (pas de pagination profonde côté social) ; le BFF découpe ensuite
 * ses propres pages. Les **compteurs** (lus sur chaque `/api/me` et fiche joueur) sont mis en
 * cache (Caffeine, TTL 30 s) et purgés après un follow/unfollow — localement par la réplique qui
 * a traité l'écriture (fast-path) et sur **toutes** les répliques par
 * {@code CacheInvalidationHandler} (événements social, groupe instance-scopé).
 */
@Service
public class FollowLookup {

    public static final int MAX_LIST_SIZE = 200;

    /** Fan-out de présence : tous les abonnés, sans plafond. */
    private static final int NO_LIMIT = Integer.MAX_VALUE;

    private static final Duration COUNTS_TTL = Duration.ofSeconds(30);
    private static final long COUNTS_MAX_SIZE = 10_000;

    /**
     * Fenêtre no-cache après invalidation : la projection social peut être en retard de quelques
     * millisecondes sur l'événement, on ne mémorise pas une lecture périmée (même garde que
     * {@link ProfileLookup}).
     */
    private static final Duration REFRESH_GRACE = Duration.ofSeconds(3);

    private final QueryGateway queryGateway;
    private final Cache<String, UserFollowCounts> countsCache = Caffeine.newBuilder()
            .expireAfterWrite(COUNTS_TTL)
            .maximumSize(COUNTS_MAX_SIZE)
            .build();
    private final Cache<String, Instant> recentlyInvalidated = Caffeine.newBuilder()
            .expireAfterWrite(REFRESH_GRACE)
            .maximumSize(COUNTS_MAX_SIZE)
            .build();

    public FollowLookup(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    /** Identifiants des sujets suivis, plus récents d'abord. */
    public CompletableFuture<List<String>> followedTopicIds(String userId, int limit) {
        return queryGateway
                .query(new TopicFollowerQuery.GetTopicFollowsQuery(userId, limit),
                        QueryResponseTypes.multipleInstancesOf(TopicFollower.class))
                .thenApply(follows -> follows.stream().map(TopicFollower::topicId).toList());
    }

    /** Identifiants des joueurs suivis, plus récents d'abord. */
    public CompletableFuture<List<String>> followingIds(String userId, int limit) {
        return queryGateway
                .query(new UserFollowerQuery.GetUserFollowsQuery(userId, FollowDirection.FOLLOWING, limit),
                        QueryResponseTypes.multipleInstancesOf(UserFollower.class))
                .thenApply(follows -> follows.stream().map(UserFollower::followedId).toList());
    }

    /**
     * Identifiants des abonnés d'un joueur (ceux qui le suivent), sans plafond : utilisé par le
     * fan-out éphémère « X est en ligne » vers les abonnés d'un joueur qui vient de se connecter.
     */
    public CompletableFuture<List<String>> followerIds(String userId) {
        return queryGateway
                .query(new UserFollowerQuery.GetUserFollowsQuery(userId, FollowDirection.FOLLOWERS, NO_LIMIT),
                        QueryResponseTypes.multipleInstancesOf(UserFollower.class))
                .thenApply(follows -> follows.stream().map(UserFollower::followerId).toList());
    }

    public CompletableFuture<UserFollowCounts> userCounts(String userId) {
        if (recentlyInvalidated.getIfPresent(userId) == null) {
            UserFollowCounts cached = countsCache.getIfPresent(userId);
            if (cached != null) {
                return CompletableFuture.completedFuture(cached);
            }
        }
        return queryGateway.query(
                        new UserFollowerQuery.GetUserFollowCountsQuery(userId),
                        QueryResponseTypes.instanceOf(UserFollowCounts.class))
                .thenApply(counts -> {
                    if (counts != null && recentlyInvalidated.getIfPresent(userId) == null) {
                        countsCache.put(userId, counts);
                    }
                    return counts;
                });
    }

    /** Purge les compteurs (follow/unfollow : le follower et le suivi changent). */
    public void invalidateCounts(String userId) {
        countsCache.invalidate(userId);
        recentlyInvalidated.put(userId, Instant.now());
    }

    /** Un compte supprimé peut modifier les compteurs de plusieurs joueurs suivis. */
    public void invalidateAllCounts() {
        countsCache.invalidateAll();
    }

    public CompletableFuture<Boolean> userFollowed(String followerId, String followedId) {
        return queryGateway.query(
                new UserFollowerQuery.ExistsUserFollowerQuery(followerId, followedId),
                QueryResponseTypes.instanceOf(Boolean.class));
    }

    public CompletableFuture<Boolean> topicFollowed(String topicId, String userId) {
        return queryGateway.query(
                new TopicFollowerQuery.ExistsTopicFollowerQuery(topicId, userId),
                QueryResponseTypes.instanceOf(Boolean.class));
    }
}
