package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.out.messaging.response.RoomPresenceView;
import com.joutio.matchmaking.domain.event.RoomEvent;
import com.joutio.matchmaking.domain.model.RoomLeaveReason;
import com.joutio.microservice.core.domain.constant.JoutioConstants;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RoomPresencePublisherTest {

    private static final Instant AT = Instant.parse("2026-11-16T10:00:00Z");

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final RoomPresencePublisher publisher = new RoomPresencePublisher(messagingTemplate);

    @Test
    void pushesTheEntryToEveryHumanOfTheRoom() {
        publisher.onEntered(new RoomEvent.RoomEnteredEvent(
                "room-1", "opponent", null, AT, "session", Set.of("initiator", "opponent")));

        EventEnvelopeResponse expected = EventEnvelopeResponse.of("room-1", 0, AT, RoomPresencePublisher.EVENT_TYPE,
                new RoomPresenceView("room-1", "opponent", true, AT));
        verify(messagingTemplate).convertAndSend("/topic/notifications/initiator", expected);
        verify(messagingTemplate).convertAndSend("/topic/notifications/opponent", expected);
    }

    @Test
    void pushesTheDeparture() {
        publisher.onLeft(new RoomEvent.RoomLeftEvent(
                "room-1", "initiator", AT, "session", RoomLeaveReason.LEFT, Set.of("initiator", "opponent")));

        verify(messagingTemplate).convertAndSend(
                "/topic/notifications/opponent",
                EventEnvelopeResponse.of("room-1", 0, AT, RoomPresencePublisher.EVENT_TYPE,
                        new RoomPresenceView("room-1", "initiator", false, AT)));
    }

    @Test
    void ignoresTheBotAndEventsWithoutPlayers() {
        publisher.onEntered(new RoomEvent.RoomEnteredEvent(
                "room-1", JoutioConstants.SYSTEM_USER_ID, null, AT, null, Set.of("initiator")));
        publisher.onEntered(new RoomEvent.RoomEnteredEvent("room-1", "initiator", null, AT, null, null));

        verifyNoInteractions(messagingTemplate);
    }
}
