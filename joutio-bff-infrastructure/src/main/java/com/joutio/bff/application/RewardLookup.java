package com.joutio.bff.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.profile.domain.model.PlayerRewards;
import com.joutio.profile.domain.query.RewardQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Récompenses d'un joueur avec cache local court, invalidé sur {@code RewardGrantedEvent}. */
@Service
public class RewardLookup {

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final Duration REFRESH_GRACE = Duration.ofSeconds(3);
    private static final long MAX_SIZE = 10_000;

    private final QueryGateway queryGateway;
    private final Cache<String, PlayerRewards> cache = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(MAX_SIZE)
            .build();
    private final Cache<String, java.time.Instant> recentlyInvalidated = Caffeine.newBuilder()
            .expireAfterWrite(REFRESH_GRACE)
            .maximumSize(MAX_SIZE)
            .build();

    public RewardLookup(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    public CompletableFuture<PlayerRewards> get(String userId) {
        PlayerRewards cached = cache.getIfPresent(userId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return queryGateway
                .query(new RewardQuery.GetPlayerRewardsQuery(userId),
                        QueryResponseTypes.instanceOf(PlayerRewards.class))
                .thenApply(rewards -> {
                    PlayerRewards resolved = rewards == null ? PlayerRewards.empty(userId) : rewards;
                    // Après une invalidation, la projection peut encore être en retard :
                    // ne pas figer une réponse vide/périmée pour tout le TTL.
                    if (recentlyInvalidated.getIfPresent(userId) == null) {
                        cache.put(userId, resolved);
                    }
                    return resolved;
                });
    }

    public void invalidate(String userId) {
        cache.invalidate(userId);
        recentlyInvalidated.put(userId, java.time.Instant.now());
    }
}
