package com.joutio.bff.application;

import com.joutio.bff.application.live.LiveCache;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.profile.domain.model.PlayerProgress;
import com.joutio.profile.domain.model.PlayerProgressUpdate;
import com.joutio.profile.domain.model.PlayerUnlocks;
import com.joutio.profile.domain.model.TopicProgress;
import com.joutio.profile.domain.query.ProgressionQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Progression d'un joueur, en cache tenu à jour par abonnement ({@code WatchProgressionsQuery}) : elle est lue sur
 * quasi chaque écran (`/api/me`, fiche joueur, enfilage matchmaking). Badges compris : un déblocage la met à jour.
 */
@Service
public class ProgressionLookup {

    private final QueryGateway queryGateway;
    private final LiveCache<PlayerProgressUpdate, PlayerProgress> progressions;

    public ProgressionLookup(QueryGateway queryGateway,
                             LiveCache<PlayerProgressUpdate, PlayerProgress> progressions) {
        this.queryGateway = queryGateway;
        this.progressions = progressions;
    }

    public CompletableFuture<PlayerProgress> get(String userId) {
        return progressions.get(userId, id -> queryGateway.query(new ProgressionQuery.GetProgressionQuery(id),
                QueryResponseTypes.instanceOf(PlayerProgress.class)));
    }

    /** XP de maîtrise du joueur dans un sujet (difficulté des questions de la salle). */
    public CompletableFuture<TopicProgress> getTopic(String userId, String topicId) {
        return queryGateway.query(
                new ProgressionQuery.GetTopicProgressionQuery(userId, topicId),
                QueryResponseTypes.instanceOf(TopicProgress.class));
    }

    /**
     * Déblocages datés du joueur (titres thématiques et badges), sans cache : la liste des titres
     * et le contrôle du titre porté doivent voir un déblocage dès qu'il est projeté.
     */
    public CompletableFuture<PlayerUnlocks> getUnlocks(String userId) {
        return queryGateway
                .query(new ProgressionQuery.GetUnlocksQuery(userId),
                        QueryResponseTypes.instanceOf(PlayerUnlocks.class))
                .thenApply(unlocks -> unlocks == null ? PlayerUnlocks.empty(userId) : unlocks);
    }
}
