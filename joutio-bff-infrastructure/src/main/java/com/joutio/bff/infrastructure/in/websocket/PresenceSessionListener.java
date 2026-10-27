package com.joutio.bff.infrastructure.in.websocket;

import com.joutio.profile.domain.command.PresenceCommand;
import com.joutio.profile.domain.model.PresenceRules;
import jakarta.annotation.PreDestroy;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Le BFF est la seule surface STOMP : il traduit le cycle de vie des sessions client
 * (CONNECT/DISCONNECT) en commandes de présence envoyées à {@code joutio-profile}.
 * Le {@code Principal} est posé par le {@code StompAuthChannelInterceptor} du SDK
 * (nom = claim {@code user_id}).
 *
 * <p>Chaque instance BFF porte un identifiant stable ({@code application:host}) : ses sessions
 * sont rattachées à cet identifiant, et un redémarrage purge uniquement les siennes (ouvertes
 * avant son démarrage).</p>
 *
 * <p><b>Bail de session</b> : le BFF renouvelle en batch le bail de ses sessions locales toutes
 * les {@link PresenceRules#SESSION_RENEW_INTERVAL}. Si l'instance disparaît sans déconnexion
 * propre, les baux expirent ({@link PresenceRules#SESSION_LEASE_TTL}) et le balayeur de
 * {@code joutio-profile} libère les joueurs — aucune session fantôme.</p>
 *
 * <p><b>Fiabilité</b> : au démarrage, la découverte du command bus distribué peut ne pas encore
 * connaître {@code joutio-profile}, et un envoi fire-and-forget perdrait la commande. Toutes les
 * commandes de présence sont donc retentées tant que le routage n'est pas prêt
 * ({@link NoHandlerForCommandException}), dans une fenêtre bornée.</p>
 */
@Component
public class PresenceSessionListener {

    private static final Logger logger = LoggerFactory.getLogger(PresenceSessionListener.class);
    private static final int MAX_ROUTING_ATTEMPTS = 30;
    private static final int MAX_RENEW_ATTEMPTS = 3;
    private static final long ROUTING_RETRY_DELAY_SECONDS = 1;

    private final CommandGateway commandGateway;
    private final String instanceId;
    private final Instant startedAt = Instant.now();
    private final Map<String, String> localSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService renewer;

    public PresenceSessionListener(CommandGateway commandGateway,
                                   @Value("${spring.application.name:joutio-bff}") String applicationName,
                                   @Value("${HOSTNAME:localhost}") String hostname) {
        this.commandGateway = commandGateway;
        this.instanceId = applicationName + ":" + hostname;
        this.renewer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "presence-renewer");
            thread.setDaemon(true);
            return thread;
        });
        long intervalMillis = PresenceRules.SESSION_RENEW_INTERVAL.toMillis();
        this.renewer.scheduleWithFixedDelay(this::renewSessions, intervalMillis, intervalMillis,
                TimeUnit.MILLISECONDS);
        logger.info("Présence: renouvellement des baux toutes les {}s (instance {})",
                PresenceRules.SESSION_RENEW_INTERVAL.toSeconds(), instanceId);
    }

    /** Purge les sessions laissées par une incarnation précédente de cette instance. */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Présence: purge des sessions de l'instance {} antérieures à {}", instanceId, startedAt);
        sendWithRoutingRetry(new PresenceCommand.ResetInstanceSessionsCommand(instanceId, startedAt),
                "reset-instance " + instanceId, 1, MAX_ROUTING_ATTEMPTS);
    }

    @EventListener
    public void onSessionConnected(SessionConnectedEvent event) {
        Principal user = event.getUser();
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (user == null || sessionId == null) {
            return;
        }
        registerSession(sessionId, user.getName());
        logger.debug("Présence: session connectée userId={}, sessionId={}", user.getName(), sessionId);
        sendWithRoutingRetry(new PresenceCommand.ConnectPlayerCommand(sessionId, user.getName(), instanceId),
                "connect " + sessionId, 1, MAX_ROUTING_ATTEMPTS);
    }

    @EventListener
    public void onSessionDisconnected(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (user == null || sessionId == null) {
            return;
        }
        unregisterSession(sessionId);
        logger.debug("Présence: session déconnectée userId={}, sessionId={}", user.getName(), sessionId);
        sendWithRoutingRetry(new PresenceCommand.DisconnectPlayerCommand(sessionId, user.getName()),
                "disconnect " + sessionId, 1, MAX_ROUTING_ATTEMPTS);
    }

    /** Renouvelle en une commande le bail de toutes les sessions locales (heartbeat batch). */
    void renewSessions() {
        if (localSessions.isEmpty()) {
            return;
        }
        List<String> sessionIds = List.copyOf(localSessions.keySet());
        sendWithRoutingRetry(new PresenceCommand.RenewPresenceSessionsCommand(sessionIds),
                "renew " + sessionIds.size() + " session(s)", 1, MAX_RENEW_ATTEMPTS);
    }

    void registerSession(String sessionId, String userId) {
        localSessions.put(sessionId, userId);
    }

    void unregisterSession(String sessionId) {
        localSessions.remove(sessionId);
    }

    /**
     * Arrêt gracieux : stoppe le renouvellement et signale la fermeture des sessions locales
     * (best-effort ; le TTL du bail couvre le reste).
     */
    @PreDestroy
    public void shutdown() {
        renewer.shutdownNow();
        localSessions.forEach((sessionId, userId) -> {
            try {
                commandGateway.send(new PresenceCommand.DisconnectPlayerCommand(sessionId, userId))
                        .exceptionally(error -> null);
            } catch (Exception exception) {
                logger.debug("Présence: déconnexion de sortie ignorée pour {}", sessionId);
            }
        });
    }

    private void sendWithRoutingRetry(Object command, String description, int attempt, int maxAttempts) {
        commandGateway.send(command).whenComplete((result, error) -> {
            if (error == null) {
                return;
            }
            if (!isRoutingNotReady(error)) {
                logger.warn("Présence: commande {} en échec", description, error);
                return;
            }
            if (attempt >= maxAttempts) {
                logger.warn("Présence: commande {} abandonnée après {} tentatives (routage indisponible)",
                        description, attempt);
                return;
            }
            CompletableFuture.delayedExecutor(ROUTING_RETRY_DELAY_SECONDS, TimeUnit.SECONDS)
                    .execute(() -> sendWithRoutingRetry(command, description, attempt + 1, maxAttempts));
        });
    }

    private static boolean isRoutingNotReady(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof NoHandlerForCommandException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
