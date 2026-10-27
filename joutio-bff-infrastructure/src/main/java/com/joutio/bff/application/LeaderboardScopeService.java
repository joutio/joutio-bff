package com.joutio.bff.application;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Résout la portée d'un classement (monde / abonnements / pays) côté BFF : le service
 * leaderboard reçoit directement les {@code memberIds} / {@code country} déjà résolus.
 * Composition asynchrone (aucun {@code join} bloquant).
 */
@Service
public class LeaderboardScopeService {

    private final FollowLookup followLookup;
    private final ProfileLookup profileLookup;

    public LeaderboardScopeService(FollowLookup followLookup, ProfileLookup profileLookup) {
        this.followLookup = followLookup;
        this.profileLookup = profileLookup;
    }

    public CompletableFuture<ScopeFilter> resolve(String scope, String requesterId) {
        if (scope == null || requesterId == null || "world".equalsIgnoreCase(scope)) {
            return CompletableFuture.completedFuture(ScopeFilter.world());
        }

        if ("following".equalsIgnoreCase(scope) || "friends".equalsIgnoreCase(scope)) {
            return followLookup.followingIds(requesterId, FollowLookup.MAX_LIST_SIZE)
                    .thenApply(followedIds -> {
                        LinkedHashSet<String> memberIds = new LinkedHashSet<>(followedIds);
                        memberIds.add(requesterId);
                        return new ScopeFilter(new ArrayList<>(memberIds), null);
                    });
        }

        if ("country".equalsIgnoreCase(scope)) {
            return profileLookup.get(requesterId)
                    .thenApply(profile -> profile.country() == null
                            ? ScopeFilter.world()
                            : new ScopeFilter(null, profile.country()));
        }

        return CompletableFuture.completedFuture(ScopeFilter.world());
    }

    public record ScopeFilter(List<String> memberIds, String country) {

        static ScopeFilter world() {
            return new ScopeFilter(null, null);
        }
    }
}
