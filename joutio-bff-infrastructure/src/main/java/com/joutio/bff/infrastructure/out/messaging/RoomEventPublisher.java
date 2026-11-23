package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.out.messaging.mapper.RoomEventResponseMapper;
import com.joutio.matchmaking.domain.event.RoomEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.SequenceNumber;
import org.axonframework.eventhandling.Timestamp;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Diffuse les événements de salle sur {@code /topic/rooms/{roomId}}.
 */
@Service
@ProcessingGroup("room-events")
public class RoomEventPublisher {

    private static final String DESTINATION_PREFIX = "/topic/rooms/";

    private final SimpMessagingTemplate messagingTemplate;

    public RoomEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventHandler
    public void onRoomEvent(RoomEvent event, @SequenceNumber long sequenceNumber, @Timestamp Instant timestamp) {
        RoomEventResponseMapper.toResponse(event).ifPresent(response -> {
            EventEnvelopeResponse envelope = EventEnvelopeResponse.of(
                    event.roomId(),
                    sequenceNumber,
                    timestamp,
                    response.type().name(),
                    response);
            messagingTemplate.convertAndSend(DESTINATION_PREFIX + event.roomId(), envelope);
        });
    }
}
