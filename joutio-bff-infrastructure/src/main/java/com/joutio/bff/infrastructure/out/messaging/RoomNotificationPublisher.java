package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.out.messaging.mapper.RoomEventNotificationMapper;
import com.joutio.matchmaking.domain.event.RoomEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Diffuse les notifications de salle sur {@code /topic/rooms/{roomId}}.
 */
@Service
@ProcessingGroup("room-notification")
public class RoomNotificationPublisher {

    private static final Logger logger = LoggerFactory.getLogger(RoomNotificationPublisher.class);
    private static final String DESTINATION_PREFIX = "/topic/rooms/";

    private final SimpMessagingTemplate messagingTemplate;

    public RoomNotificationPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventHandler
    public void onRoomEvent(EventMessage<?> eventMessage) {
        if (!(eventMessage.getPayload() instanceof RoomEvent event)) {
            return;
        }
        if (!(eventMessage instanceof DomainEventMessage<?> domainMessage)) {
            logger.warn("Notification ignorée (sans métadonnées d'agrégat): {}", event.getClass().getSimpleName());
            return;
        }

        RoomEventNotificationMapper.toNotification(event).ifPresent(notification -> {
            EventEnvelopeResponse envelope = EventEnvelopeResponse.of(
                    event.roomId(),
                    domainMessage.getSequenceNumber(),
                    domainMessage.getTimestamp(),
                    notification.type().name(),
                    notification);
            messagingTemplate.convertAndSend(DESTINATION_PREFIX + event.roomId(), envelope);
        });
    }
}
