package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.MatchmakingViewService;
import com.joutio.bff.infrastructure.in.api.response.MatchmakingTicketView;
import com.joutio.matchmaking.domain.model.MatchmakingStatus;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MatchmakingControllerTest {

    private static final String USER_ID = "user-1";

    private final MatchmakingViewService matchmakingViewService = mock(MatchmakingViewService.class);
    private final QueryGateway queryGateway = mock(QueryGateway.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MatchmakingController(matchmakingViewService, queryGateway)).build();
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
        when(matchmakingViewService.cancel(USER_ID, "mm-1")).thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(post("/api/matchmaking/tickets/mm-1/cancel"))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

        verify(matchmakingViewService).cancel(USER_ID, "mm-1");
    }
}
