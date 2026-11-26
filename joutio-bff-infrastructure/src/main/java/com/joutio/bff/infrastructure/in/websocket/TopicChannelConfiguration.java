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

/**
 * Abonnements aux {@code /topic/} : destination exacte seulement. Un motif ({@code /topic/**}) recevrait les flux de
 * tous les joueurs, en contournant les règles d'accès de chaque destination.
 */
@Configuration(proxyBeanMethods = false)
public class TopicChannelConfiguration implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ExactTopicInterceptor());
    }

    static final class ExactTopicInterceptor implements ChannelInterceptor {
        private final AntPathMatcher pathMatcher = new AntPathMatcher();

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor frame = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (frame != null
                    && StompCommand.SUBSCRIBE.equals(frame.getCommand())
                    && frame.getDestination() != null
                    && frame.getDestination().startsWith("/topic/")
                    && pathMatcher.isPattern(frame.getDestination())) {
                throw new AccessDeniedException("STOMP topics require an exact subscription destination");
            }
            return message;
        }
    }
}
