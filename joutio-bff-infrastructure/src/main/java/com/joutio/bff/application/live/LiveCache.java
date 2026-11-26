package com.joutio.bff.application.live;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * Cache local d'une vue surveillée : les entrées sont tenues à jour par <b>un seul</b> {@code subscriptionQuery} par
 * pod, ouvert au démarrage sur la requête de surveillance du service propriétaire ({@code Watch*Query}), qui pousse
 * chaque écriture de la vue. Aucune purge, aucune fenêtre de grâce : un update remplace l'entrée en cache, et un
 * update vide (compte supprimé) la retire.
 *
 * <p>Règles :</p>
 * <ul>
 *   <li>un update ne touche que les clés en cache, chargement en cours compris (le résultat du chargement est alors
 *   écarté) ; une version inférieure ou égale à celle de l'entrée est ignorée ;</li>
 *   <li>tant que l'abonnement n'est pas établi (démarrage, perte des fournisseurs), les lectures vont à la source sans
 *   rien mettre en cache, et le cache est vidé : un update manqué ne peut pas laisser de valeur périmée ;</li>
 *   <li>la taille et l'inactivité bornent la mémoire, pas la fraîcheur.</li>
 * </ul>
 *
 * @param <U> type d'update poussé par le fournisseur
 * @param <V> valeur en cache
 */
public final class LiveCache<U, V> implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(LiveCache.class);

    private static final Duration FIRST_REOPEN = Duration.ofSeconds(1);
    private static final Duration MAX_REOPEN = Duration.ofSeconds(30);

    /** Version d'une valeur lue à la source : inconnue, tout update la remplace. */
    private static final long LOADED = 0;

    private final String name;
    private final QueryGateway queryGateway;
    private final Object watchQuery;
    private final Class<U> updateType;
    private final Function<U, String> key;
    private final ToLongFunction<U> version;
    private final Function<U, V> value;
    private final AsyncCache<String, Entry<V>> cache;
    private final ScheduledExecutorService scheduler;

    private SubscriptionQueryResult<?, U> subscription;
    private volatile boolean live;
    private boolean running;
    private int generation;
    private Duration backoff = FIRST_REOPEN;

    public LiveCache(Spec<U, V> spec, QueryGateway queryGateway) {
        this.name = spec.name();
        this.queryGateway = queryGateway;
        this.watchQuery = spec.watchQuery();
        this.updateType = spec.updateType();
        this.key = spec.key();
        this.version = spec.version();
        this.value = spec.value();
        this.cache = Caffeine.newBuilder()
                .maximumSize(spec.maximumSize())
                .expireAfterAccess(spec.idle())
                .buildAsync();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("live-cache-" + name).daemon().factory());
    }

    /**
     * Description d'un cache : la requête de surveillance, l'update qu'elle pousse, et comment en tirer la clé, la
     * version et la valeur ({@code null} = clé à retirer).
     */
    public record Spec<U, V>(String name,
                             Object watchQuery,
                             Class<U> updateType,
                             Function<U, String> key,
                             ToLongFunction<U> version,
                             Function<U, V> value,
                             long maximumSize,
                             Duration idle) {
    }

    /** Valeur de la clé : en cache, sinon lue par {@code loader} (une valeur {@code null} n'est pas mise en cache). */
    public CompletableFuture<V> get(String id, Function<String, CompletableFuture<V>> loader) {
        if (!live) {
            return loader.apply(id);
        }
        return cache.get(id, (k, _) -> loader.apply(k).thenApply(LiveCache::loaded)).thenApply(LiveCache::valueOf);
    }

    /**
     * Valeurs des clés, en cache ou lues en une fois par {@code loader} pour les manquantes. Une clé absente du résultat
     * du loader est absente du résultat.
     */
    public CompletableFuture<Map<String, V>> getAll(Collection<String> ids,
                                                    Function<List<String>, CompletableFuture<Map<String, V>>> loader) {
        List<String> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        if (!live) {
            return loader.apply(distinct);
        }
        return cache.getAll(distinct, (missing, _) -> loader.apply(List.copyOf(missing)).thenApply(values -> {
                    Map<String, Entry<V>> entries = new LinkedHashMap<>();
                    values.forEach((k, v) -> {
                        if (v != null) {
                            entries.put(k, loaded(v));
                        }
                    });
                    return entries;
                }))
                .thenApply(entries -> {
                    Map<String, V> result = new LinkedHashMap<>();
                    entries.forEach((k, entry) -> result.put(k, entry.value()));
                    return result;
                });
    }

    @Override
    public synchronized void start() {
        running = true;
        open();
    }

    @Override
    public synchronized void stop() {
        running = false;
        live = false;
        generation++;
        cancel();
        scheduler.shutdownNow();
    }

    @Override
    public synchronized boolean isRunning() {
        return running;
    }

    boolean live() {
        return live;
    }

    private synchronized void open() {
        if (!running) {
            return;
        }
        int current = ++generation;
        try {
            subscription = queryGateway.subscriptionQuery(watchQuery, ResponseTypes.multipleInstancesOf(updateType),
                    ResponseTypes.instanceOf(updateType));
        } catch (RuntimeException failure) {
            failed(current, failure);
            return;
        }
        subscription.initialResult().subscribe(
                _ -> {
                },
                failure -> failed(current, failure),
                () -> opened(current));
        subscription.updates().subscribe(
                update -> updated(current, update),
                failure -> failed(current, failure),
                () -> failed(current, new IllegalStateException("watch completed by its provider")));
    }

    /** Abonnement établi : tout update à venir est reçu, le cache peut resservir. */
    private synchronized void opened(int current) {
        if (current != generation) {
            return;
        }
        cache.synchronous().invalidateAll();
        live = true;
        backoff = FIRST_REOPEN;
        logger.info("Live cache {} subscribed", name);
    }

    private void updated(int current, U update) {
        if (current != generation) {
            return;
        }
        String id = key.apply(update);
        V next = value.apply(update);
        if (next == null) {
            cache.synchronous().invalidate(id);
            return;
        }
        long nextVersion = version.applyAsLong(update);
        cache.asMap().computeIfPresent(id, (_, entry) -> {
            Entry<V> held = entry.isDone() && !entry.isCompletedExceptionally() ? entry.join() : null;
            return held != null && held.version() >= nextVersion
                    ? entry
                    : CompletableFuture.completedFuture(new Entry<>(nextVersion, next));
        });
    }

    private synchronized void failed(int current, Throwable failure) {
        if (current != generation || !running) {
            return;
        }
        live = false;
        generation++;
        cancel();
        cache.synchronous().invalidateAll();
        Duration delay = backoff;
        backoff = backoff.multipliedBy(2).compareTo(MAX_REOPEN) > 0 ? MAX_REOPEN : backoff.multipliedBy(2);
        logger.warn("Live cache {} lost its providers, reading from the source and reopening in {} ms: {}", name,
                delay.toMillis(), failure.toString());
        try {
            scheduler.schedule(this::open, delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException stopped) {
            // Pod en arrêt.
        }
    }

    private void cancel() {
        if (subscription != null) {
            subscription.cancel();
            subscription = null;
        }
    }

    private static <V> Entry<V> loaded(V value) {
        return value == null ? null : new Entry<>(LOADED, value);
    }

    private static <V> V valueOf(Entry<V> entry) {
        return entry == null ? null : entry.value();
    }

    private record Entry<V>(long version, V value) {
    }
}
