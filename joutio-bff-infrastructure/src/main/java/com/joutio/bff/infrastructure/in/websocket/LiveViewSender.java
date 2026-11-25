package com.joutio.bff.infrastructure.in.websocket;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Envois du pont des vues vivantes : à tous les abonnés d'une destination (par le broker), ou à un seul abonnement
 * (directement sur le canal sortant, comme une réponse {@code @SubscribeMapping}).
 */
@Component
public class LiveViewSender {

    private final SimpMessagingTemplate broker;
    private final SimpMessagingTemplate subscriber;

    public LiveViewSender(@Qualifier("brokerMessagingTemplate") SimpMessagingTemplate broker,
                          @Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel) {
        this.broker = broker;
        this.subscriber = new SimpMessagingTemplate(clientOutboundChannel);
        this.subscriber.setMessageConverter(broker.getMessageConverter());
    }

    /** À tous les abonnés de la destination. */
    public void broadcast(String destination, Object payload) {
        broker.convertAndSend(destination, payload);
    }

    /** Au seul abonnement {@code subscriptionId} de la session {@code sessionId}. */
    public void sendTo(String sessionId, String subscriptionId, String destination, Object payload) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(sessionId);
        headers.setSubscriptionId(subscriptionId);
        headers.setLeaveMutable(true);
        subscriber.convertAndSend(destination, payload, headers.getMessageHeaders());
    }
}
