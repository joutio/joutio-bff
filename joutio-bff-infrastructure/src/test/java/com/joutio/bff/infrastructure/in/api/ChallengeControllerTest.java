package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.ChallengeViewService;
import com.joutio.bff.infrastructure.in.api.request.CreateChallengeRequest;
import com.joutio.bff.infrastructure.in.api.response.ChallengeView;
import com.joutio.bff.infrastructure.in.api.response.TopicRefView;
import com.joutio.bff.infrastructure.in.api.response.UserRefView;
import com.joutio.matchmaking.domain.model.ChallengeStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChallengeControllerTest {

    private static final String USER_ID = "user-1";

    private final ChallengeViewService challengeViewService = mock(ChallengeViewService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ChallengeController(challengeViewService)).build();
        TestSecurity.authenticate(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    void create_challenge_returns_created_with_location() throws Exception {
        when(challengeViewService.create(eq(USER_ID), any(CreateChallengeRequest.class)))
                .thenReturn(CompletableFuture.completedFuture("challenge-1"));

        MvcResult result = mockMvc.perform(post("/api/challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topicId":"topic-1","opponentId":"opponent-1","gameId":"game-1"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/challenges/challenge-1")));

        org.mockito.ArgumentCaptor<CreateChallengeRequest> captor =
                org.mockito.ArgumentCaptor.forClass(CreateChallengeRequest.class);
        verify(challengeViewService).create(eq(USER_ID), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().gameId()).isEqualTo("game-1");
    }

    @Test
    void get_delegates() throws Exception {
        ChallengeView view = new ChallengeView(
                "challenge-1",
                new TopicRefView("topic-1", java.util.Map.of(com.joutio.microservice.core.domain.model.i18n.Language.FR, "Culture"), "GENERAL", "🌍", "#fff", null, null),
                new UserRefView("challenger-1", "Alice", null),
                new UserRefView("opponent-1", "Bob", null),
                ChallengeStatus.PENDING,
                null,
                null,
                Instant.parse("2026-10-05T10:00:00Z"),
                Instant.parse("2026-10-05T11:00:00Z"));
        when(challengeViewService.get("challenge-1")).thenReturn(CompletableFuture.completedFuture(view));

        MvcResult result = mockMvc.perform(get("/api/challenges/challenge-1"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
    }

    @Test
    void accept_decline_cancel_delegate_to_service() throws Exception {
        when(challengeViewService.accept("challenge-1", USER_ID)).thenReturn(CompletableFuture.completedFuture(null));
        when(challengeViewService.decline("challenge-1", USER_ID)).thenReturn(CompletableFuture.completedFuture(null));
        when(challengeViewService.cancel("challenge-1", USER_ID)).thenReturn(CompletableFuture.completedFuture(null));

        MvcResult accept = mockMvc.perform(post("/api/challenges/challenge-1/accept"))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(accept)).andExpect(status().isOk());

        MvcResult decline = mockMvc.perform(post("/api/challenges/challenge-1/decline"))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(decline)).andExpect(status().isOk());

        MvcResult cancel = mockMvc.perform(post("/api/challenges/challenge-1/cancel"))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(cancel)).andExpect(status().isOk());

        verify(challengeViewService).accept("challenge-1", USER_ID);
        verify(challengeViewService).decline("challenge-1", USER_ID);
        verify(challengeViewService).cancel("challenge-1", USER_ID);
    }
}
