package com.joutio.bff.application;

import com.joutio.bff.application.live.LiveCache;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.social.domain.model.FollowDirection;
import com.joutio.social.domain.model.TopicFollower;
import com.joutio.social.domain.model.UserFollowCounts;
import com.joutio.social.domain.model.UserFollowCountsUpdate;
import com.joutio.social.domain.model.UserFollower;
import com.joutio.social.domain.query.TopicFollowerQuery;
import com.joutio.social.domain.query.UserFollowerQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Lecture centralisée des follows (sujets et joueurs) pour les compositions BFF.
 * Les listes sont bornées (pas de pagination profonde côté social) ; le BFF découpe ensuite
 * ses propres pages. Les **compteurs** (lus sur chaque `/api/me` et fiche joueur) sont en cache
 * tenu à jour par abonnement ({@code WatchUserFollowCountsQuery}).
 */
@Service
public class FollowLookup {

    public static final int MAX_LIST_SIZE = 200;

    /** Fan-out de présence : tous les abonnés, sans plafond. */
    private static final int NO_LIMIT = Integer.MAX_VALUE;

    private final QueryGateway queryGateway;
    private final LiveCache<UserFollowCountsUpdate, UserFollowCounts> counts;

    public FollowLookup(QueryGateway queryGateway, LiveCache<UserFollowCountsUpdate, UserFollowCounts> counts) {
        this.queryGateway = queryGateway;
        this.counts = counts;
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
        return counts.get(userId, id -> queryGateway.query(
                new UserFollowerQuery.GetUserFollowCountsQuery(id),
                QueryResponseTypes.instanceOf(UserFollowCounts.class)));
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
