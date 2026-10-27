package com.joutio.bff.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.profile.domain.model.Profile;
import com.joutio.profile.domain.query.ProfileQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Résolution de profils avec cache local (Caffeine) et requête batch
 * ({@link ProfileQuery.GetProfilesByIdsQuery}) pour éviter le fan-out N+1 : le BFF est le point
 * d'agrégation, on amortit les lectures répétées et on résout les manquants en une requête.
 */
@Service
public class ProfileLookup {

    private static final Duration TTL = Duration.ofSeconds(30);

    /**
     * Fenêtre de grâce après une écriture : une lecture immédiate peut encore voir la projection
     * en retard, elle ne doit pas empoisonner le cache pour {@link #TTL} (le client reverrait
     * l'ancienne valeur à la réconciliation).
     */
    private static final Duration REFRESH_GRACE = Duration.ofSeconds(3);

    private static final long MAX_SIZE = 10_000;

    private final QueryGateway queryGateway;
    private final Cache<String, Profile> cache = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(MAX_SIZE)
            .build();
    private final Cache<String, Instant> recentlyInvalidated = Caffeine.newBuilder()
            .expireAfterWrite(REFRESH_GRACE)
            .maximumSize(MAX_SIZE)
            .build();

    public ProfileLookup(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    public CompletableFuture<Profile> get(String userId) {
        Profile cached = cache.getIfPresent(userId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return queryGateway
                .query(new ProfileQuery.GetProfileQuery(userId), QueryResponseTypes.instanceOf(Profile.class))
                .thenApply(profile -> {
                    cacheIfConverged(userId, profile);
                    return profile;
                });
    }

    /**
     * Purge l'entrée de cache d'un joueur : à appeler après toute écriture de profil réussie,
     * sinon les lectures servent l'ancien profil jusqu'à l'expiration du TTL (30 s).
     */
    public void invalidate(String userId) {
        cache.invalidate(userId);
        recentlyInvalidated.put(userId, Instant.now());
    }

    /**
     * Juste après une écriture, la projection profile peut être en retard : on ne met en cache
     * qu'une fois la fenêtre de grâce écoulée, sinon une lecture pessimiste figerait le profil
     * périmé pour 30 s (sauvegarde perçue comme perdue). Les lectures intermédiaires interrogent
     * la source sans cacher.
     */
    private void cacheIfConverged(String userId, Profile profile) {
        if (recentlyInvalidated.getIfPresent(userId) != null) {
            return;
        }
        cache.put(userId, profile);
    }

    /** Résout plusieurs profils en une requête batch (cache + complément), dans l'ordre des ids demandés. */
    public CompletableFuture<List<Profile>> getAll(List<String> userIds) {
        Map<String, Profile> resolved = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (String userId : userIds) {
            if (resolved.containsKey(userId)) {
                continue;
            }
            Profile cached = cache.getIfPresent(userId);
            if (cached != null) {
                resolved.put(userId, cached);
            } else if (!missing.contains(userId)) {
                missing.add(userId);
            }
        }

        if (missing.isEmpty()) {
            return CompletableFuture.completedFuture(orderByIds(userIds, resolved));
        }

        return queryGateway
                .query(new ProfileQuery.GetProfilesByIdsQuery(missing), QueryResponseTypes.multipleInstancesOf(Profile.class))
                .thenApply(profiles -> {
                    profiles.forEach(profile -> {
                        cacheIfConverged(profile.userId(), profile);
                        resolved.put(profile.userId(), profile);
                    });
                    return orderByIds(userIds, resolved);
                });
    }

    private static List<Profile> orderByIds(List<String> userIds, Map<String, Profile> resolved) {
        return userIds.stream()
                .map(resolved::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
