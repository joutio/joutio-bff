package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.TopicAuthoringService;
import com.joutio.bff.application.TopicViewService;
import com.joutio.bff.infrastructure.in.api.problem.BffProblemExceptionHandler;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.request.LeaderboardPeriod;
import com.joutio.bff.infrastructure.in.api.request.LeaderboardScope;
import com.joutio.bff.infrastructure.in.api.response.PageResponse;
import com.joutio.bff.infrastructure.in.api.response.TopicLeaderboardView;
import com.joutio.theme.domain.model.TopicSort;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TopicControllerTest {

    private static final String USER_ID = "user-1";

    private final TopicViewService topicViewService = mock(TopicViewService.class);
    private final TopicAuthoringService topicAuthoringService = mock(TopicAuthoringService.class);
    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TopicController(topicViewService, topicAuthoringService, commandGateway))
                .setControllerAdvice(new BffProblemExceptionHandler())
                .build();
        TestSecurity.authenticate(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    void list_uses_default_pagination() throws Exception {
        when(topicViewService.list(eq(USER_ID), isNull(), isNull(), eq(false), eq(false), isNull(), eq(0), eq(24)))
                .thenReturn(CompletableFuture.completedFuture(PageResponse.of(List.of(), 0, 24, 0)));

        MvcResult result = mockMvc.perform(get("/api/topics"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void list_forwards_mine_filter() throws Exception {
        when(topicViewService.list(eq(USER_ID), isNull(), isNull(), eq(false), eq(true), isNull(), eq(0), eq(24)))
                .thenReturn(CompletableFuture.completedFuture(PageResponse.of(List.of(), 0, 24, 0)));

        MvcResult result = mockMvc.perform(get("/api/topics").param("mine", "true"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
    }

    @Test
    void create_returns_created_location() throws Exception {
        when(topicAuthoringService.create(eq(USER_ID), any()))
                .thenReturn(CompletableFuture.completedFuture("topic-1"));

        MvcResult result = mockMvc.perform(post("/api/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "names": { "fr": "Cinéma" },
                                  "description": "Tout le cinéma",
                                  "category": "MOVIES"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/topics/topic-1"));
    }

    @Test
    void create_with_too_long_name_is_rejected() throws Exception {
        mockMvc.perform(post("/api/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"names\":{\"fr\":\"" + "a".repeat(256) + "\"},\"category\":\"MOVIES\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void update_name_sends_command_and_returns_204() throws Exception {
        when(topicAuthoringService.updateName(eq("topic-1"), eq(USER_ID), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(put("/api/topics/topic-1/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"fr\",\"name\":\"Séries\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent());
    }

    @Test
    void update_name_by_non_owner_returns_403() throws Exception {
        when(topicAuthoringService.updateName(eq("topic-1"), eq(USER_ID), any()))
                .thenReturn(CompletableFuture.failedFuture(new BffProblems.NotTopicOwnerProblem("topic-1")));

        MvcResult result = mockMvc.perform(put("/api/topics/topic-1/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"fr\",\"name\":\"Séries\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:joutio:bff:topic:notOwner"));
    }

    @Test
    void update_banner_url_sends_command_and_returns_204() throws Exception {
        when(topicAuthoringService.updateBannerUrl(eq("topic-1"), eq(USER_ID), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(put("/api/topics/topic-1/banner-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bannerUrl\":\"https://media.example.test/joutio-media/uploads/abc.jpg\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent());
        verify(topicAuthoringService).updateBannerUrl(eq("topic-1"), eq(USER_ID), any());
    }

    @Test
    void update_banner_url_with_invalid_scheme_is_rejected() throws Exception {
        mockMvc.perform(put("/api/topics/topic-1/banner-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bannerUrl\":\"ftp://example.test/banner.jpg\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(topicAuthoringService);
    }

    @Test
    void create_question_returns_created_location() throws Exception {
        when(topicAuthoringService.createQuestion(eq("topic-1"), eq(USER_ID), any()))
                .thenReturn(CompletableFuture.completedFuture("question-1"));

        MvcResult result = mockMvc.perform(post("/api/topics/topic-1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contents": [
                                    {
                                      "language": "fr",
                                      "text": "Capitale de la France ?",
                                      "answers": [
                                        {"choice": "A", "text": "Paris"},
                                        {"choice": "B", "text": "Lyon"},
                                        {"choice": "C", "text": "Marseille"},
                                        {"choice": "D", "text": "Lille"}
                                      ]
                                    }
                                  ],
                                  "correctAnswer": "A"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/topics/topic-1/questions/question-1"));
    }

    @Test
    void leaderboard_passes_requested_month() throws Exception {
        when(topicViewService.leaderboard(eq(USER_ID), eq("topic-1"), eq(LeaderboardPeriod.MONTHLY),
                eq("2026-08"), eq(LeaderboardScope.WORLD), eq(0), eq(25)))
                .thenReturn(CompletableFuture.completedFuture(
                        new TopicLeaderboardView(PageResponse.of(List.of(), 0, 25, 0), null)));

        MvcResult result = mockMvc.perform(get("/api/topics/topic-1/leaderboard")
                        .param("period", "MONTHLY")
                        .param("month", "2026-08"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.totalElements").value(0));

        verify(topicViewService).leaderboard(USER_ID, "topic-1", LeaderboardPeriod.MONTHLY,
                "2026-08", LeaderboardScope.WORLD, 0, 25);
    }

    @Test
    void follow_sends_deterministic_follow_id() throws Exception {
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture("user-1:topic-1"));

        MvcResult result = mockMvc.perform(put("/api/topics/topic-1/follow"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent());
        verify(commandGateway).send(new com.joutio.social.domain.command.TopicFollowerCommand.FollowTopicCommand(
                "user-1:topic-1", "topic-1", "user-1"));
    }
}
