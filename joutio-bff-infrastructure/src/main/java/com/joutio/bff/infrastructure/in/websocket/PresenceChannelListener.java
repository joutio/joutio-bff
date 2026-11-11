package com.joutio.bff.infrastructure.in.websocket;

import com.joutio.bff.application.MatchmakingViewService;
import com.joutio.bff.application.RoomViewService;
import com.joutio.matchmaking.domain.command.MatchmakingCommand;
import com.joutio.matchmaking.domain.model.RoomLeaveReason;
import com.joutio.microservice.core.domain.exception.BaseProblem;
import jakarta.annotation.PreDestroy;
import org.axonframework.messaging.HandlerExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canaux de présence : la présence d'un joueur dans un salon, ou sa recherche d'adversaire, est
 * <b>observée par le serveur</b> — l'interface ne déclare jamais qu'elle entre ou qu'elle part.
 * Le client s'abonne au canal tant que l'écran est ouvert ; aucun message n'y est publié.
 *
 * <table>
 *   <tr><th>Signal STOMP</th><th>{@code /topic/rooms/{id}/presence}</th>
 *       <th>{@code /topic/matchmaking/tickets/{id}/presence}</th></tr>
 *   <tr><td>SUBSCRIBE</td><td>entrée (snapshot + {@code JoinRoomCommand})</td><td>—</td></tr>
 *   <tr><td>UNSUBSCRIBE (écran quitté)</td><td>sortie {@code LEFT}</td>
 *       <td>annulation {@code PLAYER_LEFT}</td></tr>
 *   <tr><td>DISCONNECT</td><td>sortie {@code DISCONNECTED} après la grâce</td>
 *       <td>annulation {@code PLAYER_LEFT}</td></tr>
 * </table>
 *
 * <p>Chaque présence porte la session STOMP qui l'a ouverte : une sortie (même retardée, même
 * depuis une instance qui a perdu la connexion) ne s'applique qu'à sa propre session, si bien
 * qu'aucune minuterie partagée entre instances n'est nécessaire. Un refus d'entrée (salle pleine,
 * non invité, sujet indisponible, salle disparue) est poussé au joueur sur
 * {@code /user/queue/room-presence}. Une instance tombée sans prévenir est couverte par
 * {@code joutio-matchmaking} ({@code PlayerWentOfflineEvent} de {@code joutio-profile}).
 */
@Component
public class PresenceChannelListener {

    private static final Logger logger = LoggerFactory.getLogger(PresenceChannelListener.class);

    static final Pattern ROOM_CHANNEL = Pattern.compile("^/topic/rooms/([^/]+)/presence$");
    static final Pattern TICKET_CHANNEL =
            Pattern.compile("^/topic/matchmaking/tickets/([^/]+)/presence$");
    static final String ERROR_QUEUE = "/queue/room-presence";

    enum Kind {
        ROOM,
        TICKET
    }

    record Channel(Kind kind, String id, String userId) {}

    private final RoomViewService roomViewService;
    private final MatchmakingViewService matchmakingViewService;
    private final SimpMessagingTemplate messagingTemplate;
    private final Duration disconnectGrace;
    private final ScheduledExecutorService scheduler;

    /** Canaux ouverts par session : sessionId → subscriptionId → canal. */
    private final Map<String, Map<String, Channel>> channels = new ConcurrentHashMap<>();

    public PresenceChannelListener(
            RoomViewService roomViewService,
            MatchmakingViewService matchmakingViewService,
            SimpMessagingTemplate messagingTemplate,
            @Value("${joutio.room-presence.disconnect-grace:10s}") Duration disconnectGrace) {
        this.roomViewService = roomViewService;
        this.matchmakingViewService = matchmakingViewService;
        this.messagingTemplate = messagingTemplate;
        this.disconnectGrace = disconnectGrace;
        this.scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "presence-channels");
                            thread.setDaemon(true);
                            return thread;
                        });
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        Principal user = event.getUser();
        Message<byte[]> message = event.getMessage();
        String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        String subscriptionId = SimpMessageHeaderAccessor.getSubscriptionId(message.getHeaders());
        String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
        if (user == null || sessionId == null || subscriptionId == null || destination == null) {
            return;
        }
        Channel channel = channelOf(destination, user.getName());
        if (channel == null) {
            return;
        }
        channels.computeIfAbsent(sessionId, _ -> new ConcurrentHashMap<>())
                .put(subscriptionId, channel);
        if (channel.kind() == Kind.ROOM) {
            enter(channel, sessionId);
        }
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        Message<byte[]> message = event.getMessage();
        String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        String subscriptionId = SimpMessageHeaderAccessor.getSubscriptionId(message.getHeaders());
        if (sessionId == null || subscriptionId == null) {
            return;
        }
        Map<String, Channel> session = channels.get(sessionId);
        Channel channel = session == null ? null : session.remove(subscriptionId);
        if (channel != null) {
            depart(channel, sessionId, RoomLeaveReason.LEFT);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        Map<String, Channel> session = sessionId == null ? null : channels.remove(sessionId);
        if (session == null) {
            return;
        }
        session.values()
                .forEach(
                        channel -> {
                            if (channel.kind() == Kind.ROOM) {
                                // Une reconnexion pendant la grâce ouvre une nouvelle présence :
                                // cette sortie, liée à l'ancienne session, sera alors ignorée.
                                scheduler.schedule(
                                        () -> depart(channel, sessionId, RoomLeaveReason.DISCONNECTED),
                                        disconnectGrace.toMillis(),
                                        TimeUnit.MILLISECONDS);
                            } else {
                                depart(channel, sessionId, RoomLeaveReason.DISCONNECTED);
                            }
                        });
    }

    /** Arrêt propre de l'instance : ses présences s'arrêtent tout de suite (best-effort). */
    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        channels.forEach(
                (sessionId, session) ->
                        session.values()
                                .forEach(
                                        channel ->
                                                depart(
                                                        channel,
                                                        sessionId,
                                                        RoomLeaveReason.DISCONNECTED)));
        channels.clear();
    }

    int openChannels() {
        return channels.values().stream().mapToInt(Map::size).sum();
    }

    private void enter(Channel channel, String sessionId) {
        CompletableFuture<Void> joined;
        try {
            joined = roomViewService.join(channel.id(), channel.userId(), sessionId);
        } catch (RuntimeException error) {
            joined = CompletableFuture.failedFuture(error);
        }
        joined.exceptionally(
                error -> {
                    Map<String, Object> problem = problemOf(error);
                    logger.info(
                            "Présence salon refusée: roomId={}, userId={}, type={}",
                            channel.id(),
                            channel.userId(),
                            problem.get("type"));
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("roomId", channel.id());
                    payload.put("problem", problem);
                    messagingTemplate.convertAndSendToUser(channel.userId(), ERROR_QUEUE, payload);
                    return null;
                });
    }

    private void depart(Channel channel, String sessionId, RoomLeaveReason reason) {
        CompletableFuture<Void> sent;
        try {
            sent =
                    channel.kind() == Kind.ROOM
                            ? roomViewService.leave(
                                    channel.id(), channel.userId(), sessionId, reason)
                            : matchmakingViewService.cancel(
                                    channel.userId(),
                                    channel.id(),
                                    MatchmakingCommand.CancelMatchmakingCommand.PLAYER_LEFT);
        } catch (RuntimeException error) {
            sent = CompletableFuture.failedFuture(error);
        }
        // Une salle ou un ticket déjà purgé, un ticket déjà apparié : rien à faire.
        sent.exceptionally(
                error -> {
                    logger.debug(
                            "Présence: départ ignoré kind={}, id={}, userId={}: {}",
                            channel.kind(),
                            channel.id(),
                            channel.userId(),
                            error.getMessage());
                    return null;
                });
    }

    static Channel channelOf(String destination, String userId) {
        Matcher room = ROOM_CHANNEL.matcher(destination);
        if (room.matches()) {
            return new Channel(Kind.ROOM, room.group(1), userId);
        }
        Matcher ticket = TICKET_CHANNEL.matcher(destination);
        if (ticket.matches()) {
            return new Channel(Kind.TICKET, ticket.group(1), userId);
        }
        return null;
    }

    /** {@code type}, {@code title}, {@code detail} d'un refus, local ou remonté par le bus. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> problemOf(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        Map<String, Object> problem = new LinkedHashMap<>();
        if (current instanceof BaseProblem base) {
            problem.put("type", base.getType());
            problem.put("title", base.getTitle());
            problem.put("detail", base.getDetail());
            return problem;
        }
        if (current instanceof HandlerExecutionException remote
                && remote.getDetails().orElse(null) instanceof Map<?, ?> details) {
            Map<String, Object> map = (Map<String, Object>) details;
            problem.put("type", map.get("type"));
            problem.put("title", map.get("title"));
            problem.put("detail", map.get("detail"));
            return problem;
        }
        problem.put("type", "urn:joutio:room:presenceFailed");
        problem.put("title", "Présence indisponible");
        problem.put("detail", current == null ? null : current.getMessage());
        return problem;
    }
}
