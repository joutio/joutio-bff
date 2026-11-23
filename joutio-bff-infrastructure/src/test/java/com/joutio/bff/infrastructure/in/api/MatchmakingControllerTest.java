package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.MatchmakingViewService;
import com.joutio.bff.infrastructure.in.api.response.MatchmakingTicketView;
import com.joutio.matchmaking.domain.model.MatchmakingStatus;
import com.joutio.matchmaking.domain.event.MatchmakingEvent;
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

class MatchmakingControllerTest {

    private static final String USER_ID = "user-1";

    private final MatchmakingViewService matchmakingViewService = mock(MatchmakingViewService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MatchmakingController(matchmakingViewService)).build();
        TestSecurity.authenticate(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    void enqueue_returns_created_with_location() throws Exception {
        MatchmakingTicketView view = new MatchmakingTicketView(
                "mm-1", "topic-1", MatchmakingStatus.SEARCHING, null, null, false, Instant.now(), Instant.now());
        when(matchmakingViewService.enqueue(USER_ID, "topic-1"))
                .thenReturn(CompletableFuture.completedFuture(view));

        MvcResult result = mockMvc.perform(post("/api/matchmaking/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topicId":"topic-1"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.endsWith("/api/matchmaking/tickets/mm-1")));
    }

    @Test
    void cancel_delegates_to_service() throws Exception {
        when(matchmakingViewService.cancel(USER_ID, "mm-1", "PLAYER_CANCELLED")).thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(post("/api/matchmaking/tickets/mm-1/cancel"))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

        verify(matchmakingViewService).cancel(USER_ID, "mm-1", "PLAYER_CANCELLED");
    }

    @Test
    void events_return_the_sequenced_web_payload() throws Exception {
        Instant at = Instant.parse("2026-11-23T10:00:00Z");
        when(matchmakingViewService.events("mm-1")).thenReturn(CompletableFuture.completedFuture(
                List.of(EventEnvelope.of("mm-1", 3L, at, new MatchmakingEvent.MatchmakingExpiredEvent("mm-1", at)))));

        MvcResult result = mockMvc.perform(get("/api/matchmaking/tickets/mm-1/events"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].aggregateId").value("mm-1"))
                .andExpect(jsonPath("$[0].sequenceNumber").value(3))
                .andExpect(jsonPath("$[0].eventType").value("EXPIRED"))
                .andExpect(jsonPath("$[0].payload.type").value("EXPIRED"));
    }
}
