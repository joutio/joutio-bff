package com.joutio.bff.application;

import com.joutio.bff.application.live.LiveCache;
import com.joutio.identity.domain.exception.UserProblems;
import com.joutio.identity.domain.model.User;
import com.joutio.identity.domain.model.UserAccountStatus;
import com.joutio.identity.domain.model.UserUpdate;
import com.joutio.identity.domain.query.UserQuery;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Comptes côté identity, en cache tenu à jour par abonnement ({@code WatchUsersQuery}).
 *
 * <p>Trois usages : le filtre d'accès BFF (existence + suspension), le droit Premium
 * ({@link PremiumAccessPolicy}) et les badges publics « Compte suspendu » sur les vues où un joueur apparaît ; le
 * compte complet sert aussi {@code /api/me}.</p>
 */
@Service
public class IdentityLookup {

    private static final int MAX_BATCH = 200;

    private final QueryGateway queryGateway;
    private final LiveCache<UserUpdate, User> users;

    public IdentityLookup(QueryGateway queryGateway, LiveCache<UserUpdate, User> users) {
        this.queryGateway = queryGateway;
        this.users = users;
    }

    /**
     * Statut d'un compte. Le future échoue avec {@code UserNotFoundProblem} si le compte n'existe pas (le filtre le
     * mappe en 401).
     */
    public CompletableFuture<UserAccountStatus> status(String userId) {
        return account(userId).thenApply(IdentityLookup::toStatus);
    }

    /**
     * Statuts en lot. Les comptes inconnus sont simplement absents de la map (les vues publiques retombent sur
     * « actif »).
     */
    public CompletableFuture<Map<String, UserAccountStatus>> statuses(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        List<String> ids = userIds.stream().filter(id -> id != null && !id.isBlank()).toList();
        return users.getAll(ids, this::loadAll).thenApply(found -> {
            Map<String, UserAccountStatus> result = new LinkedHashMap<>();
            found.forEach((id, user) -> result.put(id, toStatus(user)));
            return result;
        });
    }

    /**
     * Compte complet (identity), pour les seules vues du joueur concerné : il porte la date de naissance privée.
     * Le future échoue avec {@code UserNotFoundProblem} si le compte n'existe pas. On interroge
     * {@code FindUserQuery} (Optional) : une exception distante ne conserve pas toujours la cause métier, ce qui
     * faisait retomber le filtre en fail-open après un reset de base.
     */
    public CompletableFuture<User> account(String userId) {
        return users.get(userId, id -> queryGateway
                .query(new UserQuery.FindUserQuery(id), QueryResponseTypes.optionalInstanceOf(User.class))
                .thenApply(user -> user.orElseThrow(() -> new UserProblems.UserNotFoundProblem(id))));
    }

    /** Premium accordé et effectif (badge des vues joueur) ; {@code ROLE_ADMIN} n'y compte pas. */
    public static boolean premium(UserAccountStatus status, Instant now) {
        return status != null && status.premiumActive(now);
    }

    /** Statut effectif : une suspension temporaire échue est active. */
    public static boolean suspended(UserAccountStatus status, Instant now) {
        return status != null && status.suspended(now);
    }

    private CompletableFuture<Map<String, User>> loadAll(List<String> userIds) {
        List<String> batch = userIds.size() > MAX_BATCH ? userIds.subList(0, MAX_BATCH) : userIds;
        return queryGateway
                .query(new UserQuery.GetUsersByIdsQuery(batch), QueryResponseTypes.multipleInstancesOf(User.class))
                .thenApply(found -> found.stream()
                        .collect(Collectors.toMap(User::userId, Function.identity(), (a, _) -> a)));
    }

    private static UserAccountStatus toStatus(User user) {
        return new UserAccountStatus(
                user.userId(),
                user.status(),
                user.suspension() == null ? null : user.suspension().suspendedUntil(),
                user.premium());
    }
}
