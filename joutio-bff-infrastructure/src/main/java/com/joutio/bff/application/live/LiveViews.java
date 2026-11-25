package com.joutio.bff.application.live;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems.LiveValueNotFoundProblem;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems.LiveViewForbiddenProblem;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems.LiveViewNotFoundProblem;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Catalogue des vues vivantes du BFF : résolution d'une destination STOMP et lecture REST d'une valeur versionnée.
 *
 * <p>La lecture REST passe par la même requête, la même règle d'accès et le même mapper que l'abonnement, sans cache
 * du BFF : la projection émet au commit, donc une lecture faite après un update en voit au moins la version.</p>
 */
@Service
public class LiveViews {

    private final List<LiveView<?, ?>> views;
    private final Map<String, VersionedLiveView<?, ?>> versioned = new HashMap<>();
    private final QueryGateway queryGateway;

    @Autowired
    public LiveViews(ObjectProvider<LiveView<?, ?>> views, QueryGateway queryGateway) {
        this(views.orderedStream().toList(), queryGateway);
    }

    public LiveViews(List<LiveView<?, ?>> views, QueryGateway queryGateway) {
        this.views = List.copyOf(views);
        this.queryGateway = queryGateway;
        Map<String, LiveView<?, ?>> prefixes = new HashMap<>();
        for (LiveView<?, ?> view : this.views) {
            LiveView<?, ?> clash = prefixes.putIfAbsent(view.destinationPrefix(), view);
            if (clash != null || !view.destinationPrefix().endsWith("/")) {
                throw new IllegalStateException("Invalid or duplicated live view destination prefix "
                        + view.destinationPrefix());
            }
            if (view instanceof VersionedLiveView<?, ?> value) {
                versioned.put(value.name(), value);
            }
        }
    }

    /** Une vue et sa clé, résolues depuis une destination STOMP. */
    public record Target(LiveView<?, ?> view, String key) {

        public String destination() {
            return view.destinationPrefix() + key;
        }
    }

    /** La vue servie sur cette destination exacte (préfixe de la vue + clé sans {@code /}). */
    public Optional<Target> resolve(String destination) {
        for (LiveView<?, ?> view : views) {
            String prefix = view.destinationPrefix();
            if (destination.startsWith(prefix)) {
                String key = destination.substring(prefix.length());
                if (!key.isEmpty() && key.indexOf('/') < 0) {
                    return Optional.of(new Target(view, key));
                }
            }
        }
        return Optional.empty();
    }

    /** Valeur courante d'une vue versionnée, lue chez le service propriétaire. */
    public CompletableFuture<LiveUpdate<?>> read(String name, String key, String userId) {
        VersionedLiveView<?, ?> view = versioned.get(name);
        if (view == null) {
            return CompletableFuture.failedFuture(new LiveViewNotFoundProblem(name));
        }
        if (!view.canSubscribe(userId, key)) {
            return CompletableFuture.failedFuture(new LiveViewForbiddenProblem(name, key));
        }
        return read(view, key);
    }

    private <V, T> CompletableFuture<LiveUpdate<?>> read(VersionedLiveView<V, T> view, String key) {
        return queryGateway.query(view.query(key), view.initialResponseType())
                .thenCompose(value -> value == null
                        ? CompletableFuture.failedFuture(new LiveValueNotFoundProblem(view.name(), key))
                        : CompletableFuture.completedFuture(view.message(key, value)));
    }
}
