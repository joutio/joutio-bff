package com.joutio.bff.application;

import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.profile.domain.query.ProfileQuery;
import com.joutio.theme.domain.query.TopicQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Vérification des références d'images dans les autres services, par le bus de requêtes (theme : couverture, bannière
 * de sujet et illustration de question ; profile : bannière de profil), fusionnée par URL. Utilisée avant toute
 * suppression d'administration : une image encore référencée ne peut être supprimée que sur demande explicite
 * ({@code force}). Toute indisponibilité remonte à l'appelant, qui échoue fermé.
 */
@Service
public class MediaReferenceChecker {

    private final QueryGateway queryGateway;

    public MediaReferenceChecker(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    /**
     * Compte, par URL, le nombre d'éléments métier qui la référencent (0 si absente).
     *
     * @param urls URLs publiques des objets (taille bornée par la page d'administration)
     */
    public CompletableFuture<Map<String, Long>> countReferences(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        CompletableFuture<List<com.joutio.theme.domain.model.ImageReferenceCount>> themeCounts =
                queryGateway.query(new TopicQuery.CountImageReferencesQuery(urls),
                        QueryResponseTypes.multipleInstancesOf(
                                com.joutio.theme.domain.model.ImageReferenceCount.class));
        CompletableFuture<List<com.joutio.profile.domain.model.ImageReferenceCount>> profileCounts =
                queryGateway.query(new ProfileQuery.CountImageReferencesQuery(urls),
                        QueryResponseTypes.multipleInstancesOf(
                                com.joutio.profile.domain.model.ImageReferenceCount.class));
        return themeCounts.thenCombine(profileCounts, (theme, profiles) -> merge(urls, theme, profiles));
    }

    private static Map<String, Long> merge(List<String> urls,
                                           List<com.joutio.theme.domain.model.ImageReferenceCount> themeCounts,
                                           List<com.joutio.profile.domain.model.ImageReferenceCount> profileCounts) {
        Map<String, Long> counts = new HashMap<>();
        urls.forEach(url -> counts.put(url, 0L));
        themeCounts.forEach(count -> counts.merge(count.imageUrl(), count.count(), Long::sum));
        profileCounts.forEach(count -> counts.merge(count.imageUrl(), count.count(), Long::sum));
        return counts;
    }
}
