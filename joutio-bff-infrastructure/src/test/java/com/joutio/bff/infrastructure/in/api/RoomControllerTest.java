package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.RoomViewService;
import com.joutio.bff.infrastructure.in.api.request.CreateBotRoomRequest;
import com.joutio.bff.infrastructure.in.api.request.CreateRoomRequest;
import com.joutio.matchmaking.domain.event.RoomEvent;
import com.joutio.matchmaking.domain.model.RoomReminderStage;
import com.joutio.microservice.core.domain.model.event.EventEnvelope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoomControllerTest {

    private static final String USER_ID = "user-1";

    private final RoomViewService roomViewService = mock(RoomViewService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RoomController(roomViewService)).build();
        TestSecurity.authenticate(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    void create_room_returns_created_with_location() throws Exception {
        when(roomViewService.create(eq(USER_ID), any(CreateRoomRequest.class)))
                .thenReturn(CompletableFuture.completedFuture("room-1"));

        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topicId":"topic-1"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/rooms/room-1")));
    }

    @Test
    void create_bot_room_returns_created_with_location() throws Exception {
        when(roomViewService.createBot(eq(USER_ID), any(CreateBotRoomRequest.class)))
                .thenReturn(CompletableFuture.completedFuture("room-1"));

        MvcResult result = mockMvc.perform(post("/api/rooms/bot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topicId":"topic-1","difficulty":"HARD"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/rooms/room-1")));
    }

    @Test
    void cancel_delegates_to_service() throws Exception {
        when(roomViewService.cancel("room-1", USER_ID)).thenReturn(CompletableFuture.completedFuture(null));

        MvcResult cancel = mockMvc.perform(post("/api/rooms/room-1/cancel"))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(cancel)).andExpect(status().isOk());

        verify(roomViewService).cancel("room-1", USER_ID);
    }

    @Test
    void remind_accept_decline_delegate_to_service() throws Exception {
        when(roomViewService.remind("room-1", USER_ID)).thenReturn(CompletableFuture.completedFuture(null));
        when(roomViewService.accept("room-1", USER_ID)).thenReturn(CompletableFuture.completedFuture(null));
        when(roomViewService.decline("room-1", USER_ID)).thenReturn(CompletableFuture.completedFuture(null));

        for (String action : List.of("remind", "accept", "decline")) {
            MvcResult result = mockMvc.perform(post("/api/rooms/room-1/" + action))
                    .andExpect(request().asyncStarted()).andReturn();
            mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        }

        verify(roomViewService).remind("room-1", USER_ID);
        verify(roomViewService).accept("room-1", USER_ID);
        verify(roomViewService).decline("room-1", USER_ID);
    }

    @Test
    void events_return_the_sequenced_web_payload() throws Exception {
        Instant at = Instant.parse("2026-11-23T10:00:00Z");
        when(roomViewService.events("room-1")).thenReturn(CompletableFuture.completedFuture(
                List.of(EventEnvelope.of("room-1", 3L, at, new RoomEvent.RoomRemindedEvent(
                        "room-1", "initiator", "opponent", RoomReminderStage.PRESENCE, "topic-1", null,
                        at.plusSeconds(3600), at, at.plusSeconds(10))))));

        MvcResult result = mockMvc.perform(get("/api/rooms/room-1/events"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].aggregateId").value("room-1"))
                .andExpect(jsonPath("$[0].sequenceNumber").value(3))
                .andExpect(jsonPath("$[0].eventType").value("ROOM_REMINDED"))
                .andExpect(jsonPath("$[0].payload.type").value("ROOM_REMINDED"));
    }
}
