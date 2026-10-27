package com.joutio.bff.infrastructure.out.media;

import com.joutio.bff.application.MediaReferenceChecker;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.profile.domain.query.ProfileQuery;
import com.joutio.theme.domain.query.TopicQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Vérification des références média via le query bus distribué : comptage theme (couverture,
 * bannière de sujet, illustration de question) + profile (bannière de profil), fusionné par URL.
 * Toute indisponibilité remonte à l'appelant, qui échoue fermé pour les suppressions.
 */
@Component
public class MediaReferenceCheckerAdapter implements MediaReferenceChecker {

    private final QueryGateway queryGateway;

    public MediaReferenceCheckerAdapter(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    @Override
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
