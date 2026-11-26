package com.joutio.bff.application;

import com.joutio.bff.application.live.LiveCache;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.profile.domain.model.Profile;
import com.joutio.profile.domain.model.ProfileUpdate;
import com.joutio.profile.domain.query.ProfileQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Profils en cache tenu à jour par abonnement ({@code WatchProfilesQuery}), avec requête batch
 * ({@link ProfileQuery.GetProfilesByIdsQuery}) pour éviter le fan-out N+1 : le BFF est le point d'agrégation.
 */
@Service
public class ProfileLookup {

    private final QueryGateway queryGateway;
    private final LiveCache<ProfileUpdate, Profile> profiles;

    public ProfileLookup(QueryGateway queryGateway, LiveCache<ProfileUpdate, Profile> profiles) {
        this.queryGateway = queryGateway;
        this.profiles = profiles;
    }

    public CompletableFuture<Profile> get(String userId) {
        return profiles.get(userId, id -> queryGateway.query(new ProfileQuery.GetProfileQuery(id),
                QueryResponseTypes.instanceOf(Profile.class)));
    }

    /** Résout plusieurs profils (cache + une requête batch pour les manquants), dans l'ordre des ids demandés. */
    public CompletableFuture<List<Profile>> getAll(List<String> userIds) {
        return profiles.getAll(userIds, missing -> queryGateway
                        .query(new ProfileQuery.GetProfilesByIdsQuery(missing),
                                QueryResponseTypes.multipleInstancesOf(Profile.class))
                        .thenApply(found -> found.stream()
                                .collect(Collectors.toMap(Profile::userId, Function.identity(), (a, _) -> a))))
                .thenApply(resolved -> orderByIds(userIds, resolved));
    }

    private static List<Profile> orderByIds(List<String> userIds, Map<String, Profile> resolved) {
        return userIds.stream()
                .map(resolved::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
