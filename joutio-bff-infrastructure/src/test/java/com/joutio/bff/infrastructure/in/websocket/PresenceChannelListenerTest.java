package com.joutio.bff.infrastructure.in.websocket;

import com.joutio.bff.application.MatchmakingViewService;
import com.joutio.bff.application.RoomViewService;
import com.joutio.matchmaking.domain.model.RoomLeaveReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresenceChannelListenerTest {

    private static final Principal USER = () -> "u1";

    private final RoomViewService rooms = mock(RoomViewService.class);
    private final MatchmakingViewService tickets = mock(MatchmakingViewService.class);
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final PresenceChannelListener listener =
            new PresenceChannelListener(rooms, tickets, messaging, Duration.ofMillis(50));

    PresenceChannelListenerTest() {
        when(rooms.join(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(rooms.leave(anyString(), anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(tickets.cancel(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @AfterEach
    void stop() {
        listener.shutdown();
    }

    @Test
    void subscribingToARoomChannel_entersTheRoomWithTheSession() {
        listener.onSubscribe(subscribe("s1", "sub-1", "/topic/rooms/r1/presence"));

        verify(rooms).join("r1", "u1", "s1");
    }

    @Test
    void otherDestinations_areIgnored() {
        listener.onSubscribe(subscribe("s1", "sub-1", "/topic/rooms/r1"));
        listener.onSubscribe(subscribe("s1", "sub-2", "/topic/matchmaking/tickets/k1"));

        verify(rooms, never()).join(anyString(), anyString(), anyString());
        assertThat(listener.openChannels()).isZero();
    }

    @Test
    void unsubscribing_leavesTheRoomAtOnce() {
        listener.onSubscribe(subscribe("s1", "sub-1", "/topic/rooms/r1/presence"));

        listener.onUnsubscribe(unsubscribe("s1", "sub-1"));

        verify(rooms).leave("r1", "u1", "s1", RoomLeaveReason.LEFT);
        assertThat(listener.openChannels()).isZero();
    }

    @Test
    void disconnecting_leavesTheRoomAfterTheGrace() {
        listener.onSubscribe(subscribe("s1", "sub-1", "/topic/rooms/r1/presence"));

        listener.onDisconnect(disconnect("s1"));

        verify(rooms, never()).leave(anyString(), anyString(), anyString(), any());
        verify(rooms, timeout(1000)).leave("r1", "u1", "s1", RoomLeaveReason.DISCONNECTED);
    }

    @Test
    void leavingTheSearchScreen_cancelsTheTicket() {
        listener.onSubscribe(subscribe("s1", "sub-1", "/topic/matchmaking/tickets/k1/presence"));
        verify(rooms, never()).join(anyString(), anyString(), anyString());

        listener.onUnsubscribe(unsubscribe("s1", "sub-1"));

        verify(tickets).cancel("u1", "k1", "PLAYER_LEFT");
    }

    @Test
    void losingTheConnection_cancelsTheTicketAtOnce() {
        listener.onSubscribe(subscribe("s1", "sub-1", "/topic/matchmaking/tickets/k1/presence"));

        listener.onDisconnect(disconnect("s1"));

        verify(tickets).cancel("u1", "k1", "PLAYER_LEFT");
    }

    @Test
    @SuppressWarnings("unchecked")
    void refusedEntry_isPushedToThePlayer() {
        when(rooms.join("r1", "u1", "s1"))
                .thenReturn(
                        CompletableFuture.failedFuture(
                                new IllegalStateException("Cette salle a déjà un second participant")));

        listener.onSubscribe(subscribe("s1", "sub-1", "/topic/rooms/r1/presence"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSendToUser(eq("u1"), eq("/queue/room-presence"), payload.capture());
        Map<String, Object> body = (Map<String, Object>) payload.getValue();
        assertThat(body).containsEntry("roomId", "r1").containsKey("problem");
    }

    @Test
    void anonymousSessions_areIgnored() {
        Message<byte[]> message =
                frame(SimpMessageType.SUBSCRIBE, "s1", "sub-1", "/topic/rooms/r1/presence");
        listener.onSubscribe(new SessionSubscribeEvent(this, message, null));

        verify(rooms, never()).join(anyString(), anyString(), anyString());
    }

    private SessionSubscribeEvent subscribe(String session, String subscription, String destination) {
        return new SessionSubscribeEvent(
                this, frame(SimpMessageType.SUBSCRIBE, session, subscription, destination), USER);
    }

    private SessionUnsubscribeEvent unsubscribe(String session, String subscription) {
        return new SessionUnsubscribeEvent(
                this, frame(SimpMessageType.UNSUBSCRIBE, session, subscription, null), USER);
    }

    private SessionDisconnectEvent disconnect(String session) {
        return new SessionDisconnectEvent(
                this,
                frame(SimpMessageType.DISCONNECT, session, null, null),
                session,
                CloseStatus.NORMAL,
                USER);
    }

    private static Message<byte[]> frame(
            SimpMessageType type, String session, String subscription, String destination) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(type);
        headers.setSessionId(session);
        if (subscription != null) {
            headers.setSubscriptionId(subscription);
        }
        if (destination != null) {
            headers.setDestination(destination);
        }
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
