package com.joutio.bff.infrastructure.in.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration(proxyBeanMethods = false)
public class RoomPresenceChannelConfiguration implements WebSocketMessageBrokerConfigurer {

    public static final String DESTINATION_PREFIX = "/topic/room-presence/";

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new RoomPresenceChannelInterceptor());
    }

    static final class RoomPresenceChannelInterceptor implements ChannelInterceptor {
        private final AntPathMatcher pathMatcher = new AntPathMatcher();

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor frame = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (frame == null || frame.getDestination() == null) {
                return message;
            }
            String destination = frame.getDestination();
            if (StompCommand.SUBSCRIBE.equals(frame.getCommand())
                    && destination.startsWith("/topic/")
                    && pathMatcher.isPattern(destination)) {
                throw new AccessDeniedException("STOMP topics require an exact subscription destination");
            }
            if (destination.startsWith(DESTINATION_PREFIX)
                    && (!StompCommand.SUBSCRIBE.equals(frame.getCommand())
                        || frame.getUser() == null
                        || !destination.equals(DESTINATION_PREFIX + frame.getUser().getName()))) {
                throw new AccessDeniedException("Room presence is private to the recipient");
            }
            return message;
        }
    }
}
