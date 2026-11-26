package com.joutio.bff.application;

import com.joutio.bff.application.live.LiveCache;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.profile.domain.model.PlayerRewards;
import com.joutio.profile.domain.model.PlayerRewardsUpdate;
import com.joutio.profile.domain.query.RewardQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/** Récompenses d'un joueur, en cache tenu à jour par abonnement ({@code WatchPlayerRewardsQuery}). */
@Service
public class RewardLookup {

    private final QueryGateway queryGateway;
    private final LiveCache<PlayerRewardsUpdate, PlayerRewards> rewards;

    public RewardLookup(QueryGateway queryGateway, LiveCache<PlayerRewardsUpdate, PlayerRewards> rewards) {
        this.queryGateway = queryGateway;
        this.rewards = rewards;
    }

    public CompletableFuture<PlayerRewards> get(String userId) {
        return rewards.get(userId, id -> queryGateway
                .query(new RewardQuery.GetPlayerRewardsQuery(id), QueryResponseTypes.instanceOf(PlayerRewards.class))
                .thenApply(found -> found == null ? PlayerRewards.empty(id) : found));
    }
}
