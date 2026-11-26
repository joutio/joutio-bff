package com.joutio.bff.infrastructure.in.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TopicChannelConfigurationTest {

    private final TopicChannelConfiguration.ExactTopicInterceptor interceptor =
            new TopicChannelConfiguration.ExactTopicInterceptor();

    @Test
    void accepts_an_exact_topic_subscription() {
        var room = frame(StompCommand.SUBSCRIBE, "/topic/rooms/r1", "player");
        assertSame(room, interceptor.preSend(room, null));
    }

    @Test
    void rejects_a_pattern_subscription() {
        assertDenied(frame(StompCommand.SUBSCRIBE, "/topic/**", "player"));
        assertDenied(frame(StompCommand.SUBSCRIBE, "/topic/live/my-rooms/*", "player"));
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
