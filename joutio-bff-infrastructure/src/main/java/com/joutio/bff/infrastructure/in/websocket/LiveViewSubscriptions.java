package com.joutio.bff.infrastructure.in.websocket;

import com.joutio.bff.application.live.LiveView;
import com.joutio.bff.application.live.LiveViews;
import jakarta.annotation.PreDestroy;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Pont des vues vivantes : <b>un seul</b> {@code subscriptionQuery} par destination STOMP et par pod, compté par
 * références sur les abonnements des sessions.
 *
 * <p>Les abonnements sont suivis <b>après</b> leur traitement par le broker ({@link LiveViewChannelConfiguration}) :
 * un abonné est déjà inscrit quand le pont lui parle, et ne manque donc aucun update.</p>
 *
 * <ul>
 *   <li>premier abonné du pod : ouverture, puis le résultat initial est diffusé à la destination ;</li>
 *   <li>abonné suivant : la dernière valeur relayée lui est envoyée, à lui seul ;</li>
 *   <li>update : diffusé si sa version dépasse la dernière relayée ;</li>
 *   <li>dernier départ ({@code UNSUBSCRIBE} ou déconnexion) : fermeture ;</li>
 *   <li>perte des fournisseurs (flux en erreur) : réouverture à délais croissants tant qu'un abonné reste, puis le
 *   résultat initial comble le trou (versions manquées diffusées) ;</li>
 *   <li>fin de la vue par le fournisseur ({@code complete}) : plus rien à relayer, pas de réouverture.</li>
 * </ul>
 *
 * <p>Les messages se préparent en parallèle (un message peut s'enrichir d'autres lectures), mais partent dans l'ordre
 * des versions : chaque envoi attend le précédent. Un message en échec est journalisé et sauté.</p>
 *
 * <p>Le client garde la plus haute version reçue, par REST ou par STOMP : une valeur reçue en double ou en retard est
 * ignorée. Le contrôle d'accès a lieu avant le broker : un abonnement refusé n'arrive jamais ici.</p>
 */
@Component
public class LiveViewSubscriptions {

    private static final Logger logger = LoggerFactory.getLogger(LiveViewSubscriptions.class);
    private static final Duration FIRST_REOPEN = Duration.ofSeconds(1);
    private static final Duration MAX_REOPEN = Duration.ofSeconds(30);

    private final LiveViews liveViews;
    private final QueryGateway queryGateway;
    private final LiveViewSender sender;
    private final ScheduledExecutorService scheduler;
    private final Duration firstReopen;
    private final Duration maxReopen;

    /** Abonnements par session : sessionId → subscriptionId → destination. */
    private final Map<String, Map<String, String>> sessions = new ConcurrentHashMap<>();
    /** Relais ouverts par destination. */
    private final Map<String, Relay<?, ?>> relays = new ConcurrentHashMap<>();

    @Autowired
    public LiveViewSubscriptions(LiveViews liveViews, QueryGateway queryGateway, LiveViewSender sender) {
        this(liveViews, queryGateway, sender, FIRST_REOPEN, MAX_REOPEN);
    }

    LiveViewSubscriptions(LiveViews liveViews, QueryGateway queryGateway, LiveViewSender sender,
                          Duration firstReopen, Duration maxReopen) {
        this.liveViews = liveViews;
        this.queryGateway = queryGateway;
        this.sender = sender;
        this.firstReopen = firstReopen;
        this.maxReopen = maxReopen;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "live-views");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Abonnement inscrit par le broker. Une destination qui n'est pas une vue vivante est ignorée. */
    public void subscribed(String sessionId, String subscriptionId, String destination) {
        liveViews.resolve(destination).ifPresent(target -> {
            String previous = sessions.computeIfAbsent(sessionId, _ -> new ConcurrentHashMap<>())
                    .put(subscriptionId, destination);
            if (previous != null) {
                release(previous);
            }
            acquire(target).snapshotTo(sessionId, subscriptionId);
        });
    }

    public void unsubscribed(String sessionId, String subscriptionId) {
        Map<String, String> session = sessions.get(sessionId);
        String destination = session == null ? null : session.remove(subscriptionId);
        if (destination != null) {
            release(destination);
        }
    }

    public void disconnected(String sessionId) {
        Map<String, String> session = sessions.remove(sessionId);
        if (session != null) {
            session.values().forEach(this::release);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        relays.values().forEach(Relay::close);
        relays.clear();
        sessions.clear();
    }

    /** Destinations dont le pod tient un abonnement ouvert. */
    int openRelays() {
        return relays.size();
    }

    private Relay<?, ?> acquire(LiveViews.Target target) {
        boolean[] created = {false};
        Relay<?, ?> relay = relays.compute(target.destination(), (destination, existing) -> {
            Relay<?, ?> current = existing;
            if (current == null) {
                current = relayOf(target.view(), target.key(), destination);
                created[0] = true;
            }
            current.subscribers++;
            return current;
        });
        if (created[0]) {
            relay.open();
        }
        return relay;
    }

    private void release(String destination) {
        Relay<?, ?>[] closing = {null};
        relays.computeIfPresent(destination, (key, relay) -> {
            if (--relay.subscribers > 0) {
                return relay;
            }
            closing[0] = relay;
            return null;
        });
        if (closing[0] != null) {
            closing[0].close();
        }
    }

    private <I, U> Relay<I, U> relayOf(LiveView<I, U> view, String key, String destination) {
        return new Relay<>(view, key, destination);
    }

    /** L'abonnement du pod à une destination. Les compteurs d'abonnés sont gardés par {@code relays.compute}. */
    private final class Relay<I, U> {

        private static final long NONE = Long.MIN_VALUE;

        private final LiveView<I, U> view;
        private final String key;
        private final String destination;
        private int subscribers;

        private SubscriptionQueryResult<I, U> result;
        /** Message du dernier update relayé, envoyé à chaque nouvel abonné. */
        private CompletableFuture<?> latest;
        /** Fin du dernier envoi : le suivant l'attend, pour garder l'ordre des versions. */
        private CompletableFuture<Void> sent = CompletableFuture.completedFuture(null);
        private long version = NONE;
        private int generation;
        private boolean closed;
        private Duration backoff = firstReopen;

        Relay(LiveView<I, U> view, String key, String destination) {
            this.view = view;
            this.key = key;
            this.destination = destination;
        }

        synchronized void open() {
            if (closed) {
                return;
            }
            int current = ++generation;
            try {
                result = queryGateway.subscriptionQuery(view.query(key), view.initialResponseType(),
                        view.updateResponseType());
            } catch (RuntimeException failure) {
                failed(current, failure);
                return;
            }
            result.initialResult().subscribe(
                    initial -> initial(current, initial),
                    failure -> failed(current, failure),
                    () -> opened(current));
            result.updates().subscribe(
                    update -> updated(current, update),
                    failure -> failed(current, failure),
                    () -> completed(current));
        }

        synchronized void close() {
            closed = true;
            generation++;
            cancel();
        }

        synchronized void snapshotTo(String sessionId, String subscriptionId) {
            if (latest != null) {
                send(latest, message -> sender.sendTo(sessionId, subscriptionId, destination, message));
            }
        }

        private synchronized void initial(int current, I initial) {
            if (current == generation) {
                view.updatesOf(initial).forEach(this::relay);
            }
        }

        private synchronized void updated(int current, U update) {
            if (current == generation) {
                relay(update);
            }
        }

        /** Résultat initial reçu (ou vide) : l'abonnement est établi. */
        private synchronized void opened(int current) {
            if (current == generation) {
                backoff = firstReopen;
            }
        }

        private void relay(U update) {
            long next = view.version(update);
            if (next <= version) {
                return;
            }
            version = next;
            latest = view.message(key, update);
            send(latest, message -> sender.broadcast(destination, message));
        }

        /** Envoie le message après les précédents ; rien s'il est {@code null}, journalisé s'il échoue. */
        private void send(CompletableFuture<?> message, Consumer<Object> delivery) {
            sent = sent.thenCompose(_ -> message.handle((value, failure) -> {
                if (failure != null) {
                    logger.warn("Live view {} skipped an update: {}", destination, failure.toString());
                } else if (value != null) {
                    delivery.accept(value);
                }
                return null;
            }));
        }

        private synchronized void failed(int current, Throwable failure) {
            if (current != generation || closed) {
                return;
            }
            generation++;
            cancel();
            Duration delay = backoff;
            backoff = backoff.multipliedBy(2).compareTo(maxReopen) > 0 ? maxReopen : backoff.multipliedBy(2);
            logger.warn("Live view {} lost its providers, reopening in {} ms: {}", destination, delay.toMillis(),
                    failure.toString());
            try {
                scheduler.schedule(this::open, delay.toMillis(), TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException stopped) {
                // Pod en arrêt : les clients se réabonneront ailleurs.
            }
        }

        private synchronized void completed(int current) {
            if (current != generation) {
                return;
            }
            generation++;
            cancel();
            logger.debug("Live view {} completed by its provider", destination);
        }

        private void cancel() {
            if (result != null) {
                result.cancel();
                result = null;
            }
        }
    }
}
