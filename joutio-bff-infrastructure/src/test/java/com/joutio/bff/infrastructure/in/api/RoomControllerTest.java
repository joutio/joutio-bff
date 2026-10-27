package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.RoomViewService;
import com.joutio.bff.infrastructure.in.api.request.CreateBotRoomRequest;
import com.joutio.bff.infrastructure.in.api.request.CreateRoomRequest;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoomControllerTest {

    private static final String USER_ID = "user-1";

    private final RoomViewService roomViewService = mock(RoomViewService.class);
    private final QueryGateway queryGateway = mock(QueryGateway.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RoomController(roomViewService, queryGateway)).build();
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
    void join_leave_cancel_delegate_to_service() throws Exception {
        when(roomViewService.join("room-1", USER_ID)).thenReturn(CompletableFuture.completedFuture(null));
        when(roomViewService.leave("room-1", USER_ID)).thenReturn(CompletableFuture.completedFuture(null));
        when(roomViewService.cancel("room-1", USER_ID)).thenReturn(CompletableFuture.completedFuture(null));

        MvcResult join = mockMvc.perform(post("/api/rooms/room-1/join"))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(join)).andExpect(status().isOk());

        MvcResult leave = mockMvc.perform(post("/api/rooms/room-1/leave"))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(leave)).andExpect(status().isOk());

        MvcResult cancel = mockMvc.perform(post("/api/rooms/room-1/cancel"))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(cancel)).andExpect(status().isOk());

        verify(roomViewService).join("room-1", USER_ID);
        verify(roomViewService).leave("room-1", USER_ID);
        verify(roomViewService).cancel("room-1", USER_ID);
    }

    @Test
    void wizz_delegates_to_service() throws Exception {
        when(roomViewService.wizz("room-1", USER_ID)).thenReturn(CompletableFuture.completedFuture(null));

        MvcResult wizz = mockMvc.perform(post("/api/rooms/room-1/wizz"))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(wizz)).andExpect(status().isOk());

        verify(roomViewService).wizz("room-1", USER_ID);
    }
}
