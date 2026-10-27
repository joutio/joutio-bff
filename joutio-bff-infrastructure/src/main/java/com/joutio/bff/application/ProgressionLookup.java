package com.joutio.bff.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.profile.domain.model.PlayerProgress;
import com.joutio.profile.domain.model.TopicProgress;
import com.joutio.profile.domain.query.ProgressionQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Progression d'un joueur avec cache local (Caffeine) : elle est lue sur quasi chaque écran
 * (`/api/me`, fiche joueur, enfilage matchmaking) et change peu. Le TTL court borne la
 * péremption après une partie — la page résultat s'appuie sur la vue autoritaire du service
 * `game` (snapshot + XP), pas sur ce cache.
 */
@Service
public class ProgressionLookup {

    private static final Duration TTL = Duration.ofSeconds(15);
    private static final long MAX_SIZE = 10_000;

    private final QueryGateway queryGateway;
    private final Cache<String, PlayerProgress> cache = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(MAX_SIZE)
            .build();

    public ProgressionLookup(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    public CompletableFuture<PlayerProgress> get(String userId) {
        PlayerProgress cached = cache.getIfPresent(userId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return queryGateway
                .query(new ProgressionQuery.GetProgressionQuery(userId),
                        QueryResponseTypes.instanceOf(PlayerProgress.class))
                .thenApply(progress -> {
                    if (progress != null) {
                        cache.put(userId, progress);
                    }
                    return progress;
                });
    }

    /** XP de maîtrise du joueur dans un sujet (difficulté des questions de la salle). */
    public CompletableFuture<TopicProgress> getTopic(String userId, String topicId) {
        return queryGateway.query(
                new ProgressionQuery.GetTopicProgressionQuery(userId, topicId),
                QueryResponseTypes.instanceOf(TopicProgress.class));
    }

    /** Purge l'entrée d'un joueur (aucune écriture BFF de progression aujourd'hui : filet). */
    public void invalidate(String userId) {
        cache.invalidate(userId);
    }
}
