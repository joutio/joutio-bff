package com.joutio.bff.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.joutio.identity.domain.exception.UserProblems;
import com.joutio.identity.domain.model.User;
import com.joutio.identity.domain.model.UserAccountStatus;
import com.joutio.identity.domain.query.UserQuery;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Statut des comptes côté identity, avec cache local (Caffeine).
 *
 * <p>Deux usages : le filtre d'accès BFF (existence + suspension) et les badges publics
 * « Compte suspendu » sur les vues où un joueur apparaît. Le cache est borné à 30 s et purgé
 * sur les événements identity (toutes les répliques via le groupe instance-scopé
 * {@code cache-invalidation}).</p>
 */
@Service
public class IdentityLookup {

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final long MAX_SIZE = 10_000;
    private static final int MAX_BATCH = 200;

    private static final Duration REFRESH_GRACE = Duration.ofSeconds(3);

    private final QueryGateway queryGateway;
    private final Cache<String, UserAccountStatus> cache = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(MAX_SIZE)
            .build();
    /** Après une invalidation, la projection identity peut être en retard : ne pas re-cacher. */
    private final Cache<String, Instant> recentlyInvalidated = Caffeine.newBuilder()
            .expireAfterWrite(REFRESH_GRACE)
            .maximumSize(MAX_SIZE)
            .build();

    public IdentityLookup(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    /**
     * Statut d'un compte. Le future échoue avec {@code UserNotFoundProblem} si le compte
     * n'existe pas (le filtre le mappe en 401). On interroge {@code FindUserQuery} (Optional)
     * plutôt que {@code GetUserAccountStatusQuery} : une exception distante traversant Axon ne
     * conserve pas toujours la cause métier, ce qui faisait retomber le filtre en fail-open
     * après un reset de base.
     */
    public CompletableFuture<UserAccountStatus> status(String userId) {
        UserAccountStatus cached = cache.getIfPresent(userId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        // `FindUserQuery` renvoie `Optional.empty()` pour un compte disparu : contrairement à
        // `GetUserAccountStatusQuery`, il ne lève pas d'exception distante que le filtre ne peut
        // pas toujours relier à `UserNotFoundProblem` (reset de base : fail-open involontaire).
        return queryGateway
                .query(new UserQuery.FindUserQuery(userId),
                        QueryResponseTypes.optionalInstanceOf(User.class))
                .thenApply(user -> user
                        .map(IdentityLookup::toStatus)
                        .orElseThrow(() -> new UserProblems.UserNotFoundProblem(userId)))
                .thenApply(status -> {
                    cacheIfConverged(userId, status);
                    return status;
                });
    }

    /**
     * Statuts en lot, avec complétion des entrées absentes du cache. Les comptes inconnus sont
     * simplement absents de la map (les vues publiques retombent sur « actif »).
     */
    public CompletableFuture<Map<String, UserAccountStatus>> statuses(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        List<String> distinct = userIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        Map<String, UserAccountStatus> result = new LinkedHashMap<>();
        List<String> missing = new java.util.ArrayList<>();
        for (String userId : distinct) {
            UserAccountStatus cached = cache.getIfPresent(userId);
            if (cached == null) {
                missing.add(userId);
            } else {
                result.put(userId, cached);
            }
        }
        if (missing.isEmpty()) {
            return CompletableFuture.completedFuture(result);
        }
        List<String> batch = missing.size() > MAX_BATCH ? missing.subList(0, MAX_BATCH) : missing;
        return queryGateway
                .query(new UserQuery.GetUserAccountStatusesQuery(batch),
                        QueryResponseTypes.multipleInstancesOf(UserAccountStatus.class))
                .thenApply(statuses -> {
                    statuses.forEach(status -> {
                        cacheIfConverged(status.userId(), status);
                        result.put(status.userId(), status);
                    });
                    return result;
                });
    }

    /** Statut effectif : une suspension temporaire échue est active. */
    public static boolean suspended(UserAccountStatus status, Instant now) {
        return status != null && status.suspended(now);
    }

    private static UserAccountStatus toStatus(User user) {
        return new UserAccountStatus(
                user.userId(),
                user.status(),
                user.suspension() == null ? null : user.suspension().suspendedUntil());
    }

    public void invalidate(String userId) {
        cache.invalidate(userId);
        recentlyInvalidated.put(userId, Instant.now());
    }

    private void cacheIfConverged(String userId, UserAccountStatus status) {
        if (recentlyInvalidated.getIfPresent(userId) != null) {
            return;
        }
        cache.put(userId, status);
    }
}
