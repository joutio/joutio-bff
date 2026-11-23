package com.joutio.bff.infrastructure.in.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoomPresenceChannelConfigurationTest {

    private final RoomPresenceChannelConfiguration.RoomPresenceChannelInterceptor interceptor =
            new RoomPresenceChannelConfiguration.RoomPresenceChannelInterceptor();

    @Test
    void accepts_presence_subscription_for_the_authenticated_recipient() {
        var own = frame(StompCommand.SUBSCRIBE, "/topic/room-presence/player", "player");
        assertSame(own, interceptor.preSend(own, null));

        var room = frame(StompCommand.SUBSCRIBE, "/topic/rooms/r1", "player");
        assertSame(room, interceptor.preSend(room, null));
    }

    @Test
    void rejects_other_recipients_unauthenticated_subscriptions_and_client_publication() {
        assertDenied(frame(StompCommand.SUBSCRIBE, "/topic/room-presence/other", "player"));
        assertDenied(frame(StompCommand.SUBSCRIBE, "/topic/room-presence/player", null));
        assertDenied(frame(StompCommand.SUBSCRIBE, "/topic/**", "player"));
        assertDenied(frame(StompCommand.SEND, "/topic/room-presence/player", "player"));
    }

    private void assertDenied(Message<?> frame) {
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(frame, null));
    }

    private static Message<byte[]> frame(StompCommand command, String destination, String userId) {
        var accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        if (userId != null) accessor.setUser(() -> userId);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
