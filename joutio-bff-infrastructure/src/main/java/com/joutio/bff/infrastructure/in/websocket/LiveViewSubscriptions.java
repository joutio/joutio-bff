package com.joutio.bff.infrastructure.in.websocket;

import com.joutio.bff.application.live.LiveView;
import com.joutio.bff.application.live.LiveViews;
import jakarta.annotation.PreDestroy;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Pont des vues vivantes : <b>un seul</b> {@code subscriptionQuery} par destination STOMP et par pod, compté par
 * références sur les sessions abonnées.
 *
 * <ul>
 *   <li>premier abonné du pod : ouverture ; le résultat initial fixe la version de départ, sans rien pousser (le client
 *   lit la valeur lui-même, après s'être abonné) ;</li>
 *   <li>update : relayé sur la destination si sa version dépasse la dernière relayée ;</li>
 *   <li>dernier départ ({@code UNSUBSCRIBE} ou déconnexion) : fermeture ;</li>
 *   <li>perte des fournisseurs (flux en erreur) : réouverture à délais croissants tant qu'un abonné reste, puis le
 *   résultat initial comble le trou (versions manquées poussées) ;</li>
 *   <li>fin de la vue par le fournisseur ({@code complete}) : plus rien à relayer, pas de réouverture.</li>
 * </ul>
 *
 * <p>Le contrôle d'accès a lieu avant, dans {@link LiveViewChannelConfiguration} : un abonnement refusé n'arrive
 * jamais ici.</p>
 */
@Component
public class LiveViewSubscriptions {

    private static final Logger logger = LoggerFactory.getLogger(LiveViewSubscriptions.class);
    private static final Duration FIRST_REOPEN = Duration.ofSeconds(1);
    private static final Duration MAX_REOPEN = Duration.ofSeconds(30);

    private final LiveViews liveViews;
    private final QueryGateway queryGateway;
    private final SimpMessagingTemplate messagingTemplate;
    private final ScheduledExecutorService scheduler;
    private final Duration firstReopen;
    private final Duration maxReopen;

    /** Abonnements par session : sessionId → subscriptionId → destination. */
    private final Map<String, Map<String, String>> sessions = new ConcurrentHashMap<>();
    /** Relais ouverts par destination. */
    private final Map<String, Relay<?, ?>> relays = new ConcurrentHashMap<>();

    @Autowired
    public LiveViewSubscriptions(LiveViews liveViews, QueryGateway queryGateway,
                                 SimpMessagingTemplate messagingTemplate) {
        this(liveViews, queryGateway, messagingTemplate, FIRST_REOPEN, MAX_REOPEN);
    }

    LiveViewSubscriptions(LiveViews liveViews, QueryGateway queryGateway, SimpMessagingTemplate messagingTemplate,
                          Duration firstReopen, Duration maxReopen) {
        this.liveViews = liveViews;
        this.queryGateway = queryGateway;
        this.messagingTemplate = messagingTemplate;
        this.firstReopen = firstReopen;
        this.maxReopen = maxReopen;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "live-views");
            thread.setDaemon(true);
            return thread;
        });
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        Message<byte[]> message = event.getMessage();
        String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        String subscriptionId = SimpMessageHeaderAccessor.getSubscriptionId(message.getHeaders());
        String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
        if (sessionId == null || subscriptionId == null || destination == null) {
            return;
        }
        liveViews.resolve(destination).ifPresent(target -> {
            String previous = sessions.computeIfAbsent(sessionId, _ -> new ConcurrentHashMap<>())
                    .put(subscriptionId, destination);
            if (previous != null) {
                release(previous);
            }
            acquire(target);
        });
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        Message<byte[]> message = event.getMessage();
        String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        String subscriptionId = SimpMessageHeaderAccessor.getSubscriptionId(message.getHeaders());
        if (sessionId == null || subscriptionId == null) {
            return;
        }
        Map<String, String> session = sessions.get(sessionId);
        String destination = session == null ? null : session.remove(subscriptionId);
        if (destination != null) {
            release(destination);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Map<String, String> session = event.getSessionId() == null ? null : sessions.remove(event.getSessionId());
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

    private void acquire(LiveViews.Target target) {
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
        private long version = NONE;
        private int generation;
        private boolean established;
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
            boolean fill = established;
            try {
                result = queryGateway.subscriptionQuery(view.query(key), view.initialResponseType(),
                        view.updateResponseType());
            } catch (RuntimeException failure) {
                failed(current, failure);
                return;
            }
            result.initialResult().subscribe(
                    initial -> initial(current, initial, fill),
                    failure -> failed(current, failure),
                    () -> established(current));
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

        private synchronized void initial(int current, I initial, boolean fill) {
            if (current != generation) {
                return;
            }
            List<U> values = view.updatesOf(initial);
            for (U value : values) {
                if (fill) {
                    relay(value);
                } else {
                    version = Math.max(version, view.version(value));
                }
            }
            if (fill) {
                logger.info("Live view {} reopened ({} value(s) in the initial result)", destination, values.size());
            }
        }

        private synchronized void updated(int current, U update) {
            if (current == generation) {
                relay(update);
            }
        }

        /** Résultat initial reçu (ou vide) : une réouverture ultérieure comblera le trou. */
        private synchronized void established(int current) {
            if (current == generation) {
                established = true;
                backoff = firstReopen;
            }
        }

        private void relay(U update) {
            long next = view.version(update);
            if (next <= version) {
                return;
            }
            version = next;
            Object message = view.message(key, update);
            if (message != null) {
                messagingTemplate.convertAndSend(destination, message);
            }
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
