package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.out.messaging.response.RoomPresenceView;
import com.joutio.bff.infrastructure.out.messaging.response.RoomStatusView;
import com.joutio.matchmaking.domain.event.RoomEvent;
import com.joutio.matchmaking.domain.model.RoomLeaveReason;
import com.joutio.matchmaking.domain.model.RoomOrigin;
import com.joutio.matchmaking.domain.model.RoomStatus;
import com.joutio.microservice.core.domain.constant.JoutioConstants;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        verify(messagingTemplate).convertAndSend("/topic/room-presence/initiator", expected);
        verify(messagingTemplate).convertAndSend("/topic/room-presence/opponent", expected);
    }

    @Test
    void pushesTheDeparture() {
        publisher.onLeft(new RoomEvent.RoomLeftEvent(
                "room-1", "initiator", AT, "session", RoomLeaveReason.LEFT, Set.of("initiator", "opponent")));

        verify(messagingTemplate).convertAndSend(
                "/topic/room-presence/opponent",
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

    @Test
    void invitation_isPushedToBothPlayers_withItsDeadline() {
        publisher.onCreated(new RoomEvent.RoomCreatedEvent("room-1", "topic-1", "initiator", "opponent", false, null,
                RoomOrigin.CHALLENGE, null, RoomStatus.INVITED, AT.plusSeconds(3600), AT));

        EventEnvelopeResponse expected = EventEnvelopeResponse.of("room-1", 0, AT, RoomPresencePublisher.STATUS_EVENT_TYPE,
                new RoomStatusView("room-1", RoomStatus.INVITED, AT.plusSeconds(3600), AT));
        verify(messagingTemplate).convertAndSend("/topic/room-presence/initiator", expected);
        verify(messagingTemplate).convertAndSend("/topic/room-presence/opponent", expected);
    }

    @Test
    void lifecycle_isPushedToThePlayersOfTheRoom_neverToTheBot() {
        publisher.onCreated(new RoomEvent.RoomCreatedEvent("room-1", "topic-1", "initiator",
                JoutioConstants.SYSTEM_USER_ID, true, null, RoomOrigin.BOT, null, RoomStatus.WAITING_PLAYERS,
                AT.plusSeconds(86_400), AT));
        publisher.onDeclined(new RoomEvent.RoomDeclinedEvent("room-2", "topic-1", "initiator", "opponent",
                RoomOrigin.CHALLENGE, AT));

        verify(messagingTemplate).convertAndSend("/topic/room-presence/initiator", EventEnvelopeResponse.of("room-1", 0, AT,
                RoomPresencePublisher.STATUS_EVENT_TYPE,
                new RoomStatusView("room-1", RoomStatus.WAITING_PLAYERS, AT.plusSeconds(86_400), AT)));
        verify(messagingTemplate).convertAndSend("/topic/room-presence/opponent", EventEnvelopeResponse.of("room-2", 0, AT,
                RoomPresencePublisher.STATUS_EVENT_TYPE, new RoomStatusView("room-2", RoomStatus.DECLINED, null, AT)));
        verify(messagingTemplate, never()).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/room-presence/" + JoutioConstants.SYSTEM_USER_ID),
                org.mockito.ArgumentMatchers.any(Object.class));
    }
}
