package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.out.messaging.mapper.MatchmakingEventNotificationMapper;
import com.joutio.matchmaking.domain.event.MatchmakingEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Diffuse les notifications d'appariement public sur
 * {@code /topic/matchmaking/tickets/{ticketId}}.
 */
@Service
@ProcessingGroup("matchmaking-notification")
public class MatchmakingNotificationPublisher {

    private static final Logger logger = LoggerFactory.getLogger(MatchmakingNotificationPublisher.class);
    private static final String DESTINATION_PREFIX = "/topic/matchmaking/tickets/";

    private final SimpMessagingTemplate messagingTemplate;

    public MatchmakingNotificationPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventHandler
    public void onMatchmakingEvent(EventMessage<?> eventMessage) {
        if (!(eventMessage.getPayload() instanceof MatchmakingEvent event)) {
            return;
        }
        if (!(eventMessage instanceof DomainEventMessage<?> domainMessage)) {
            logger.warn("Notification ignorée (sans métadonnées d'agrégat): {}", event.getClass().getSimpleName());
            return;
        }

        MatchmakingEventNotificationMapper.toNotification(event).ifPresent(notification -> {
            EventEnvelopeResponse envelope = EventEnvelopeResponse.of(
                    event.matchmakingId(),
                    domainMessage.getSequenceNumber(),
                    domainMessage.getTimestamp(),
                    notification.type().name(),
                    notification);
            messagingTemplate.convertAndSend(DESTINATION_PREFIX + event.matchmakingId(), envelope);
        });
    }
}
