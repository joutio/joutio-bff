package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.GameViewService;
import com.joutio.bff.infrastructure.in.api.problem.BffProblemExceptionHandler;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.response.ActiveGameView;
import com.joutio.bff.infrastructure.in.api.response.GameResultView;
import com.joutio.bff.infrastructure.in.api.response.TopicRefView;
import com.joutio.bff.infrastructure.in.api.response.UserRefView;
import com.joutio.game.domain.model.GamePlayerType;
import com.joutio.game.domain.model.GameQuestionChoice;
import com.joutio.game.domain.model.GameStatus;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GameControllerTest {

    private static final String USER_ID = "user-1";

    private final GameViewService gameViewService = mock(GameViewService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new GameController(gameViewService))
                .setControllerAdvice(new BffProblemExceptionHandler())
                .build();
        TestSecurity.authenticate(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    void answer_sends_choice_without_player_in_body() throws Exception {
        when(gameViewService.answer("game-1", USER_ID, GameQuestionChoice.A))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(post("/api/games/game-1/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"choice":"A"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        verify(gameViewService).answer("game-1", USER_ID, GameQuestionChoice.A);
    }

    @Test
    void abandon_is_always_accepted() throws Exception {
        when(gameViewService.abandon("game-1", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(post("/api/games/game-1/abandon"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        verify(gameViewService).abandon("game-1", USER_ID);
    }

    @Test
    void active_games_returns_the_collection() throws Exception {
        ActiveGameView view = new ActiveGameView(
                "game-1",
                new TopicRefView("topic-1", java.util.Map.of(com.joutio.microservice.core.domain.model.i18n.Language.FR, "Culture générale"), "GENERAL", "🌍", "#ffffff", null, null),
                new UserRefView("opponent-1", "Bob", null),
                GamePlayerType.HUMAN,
                GameStatus.IN_PROGRESS,
                Instant.parse("2026-10-05T10:00:00Z"));
        when(gameViewService.activeGames(USER_ID)).thenReturn(CompletableFuture.completedFuture(List.of(view)));

        MvcResult result = mockMvc.perform(get("/api/games").param("active", "true"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gameId").value("game-1"))
                .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"));
        verify(gameViewService).activeGames(USER_ID);
    }

    @Test
    void active_games_returns_empty_collection_without_game() throws Exception {
        when(gameViewService.activeGames(USER_ID)).thenReturn(CompletableFuture.completedFuture(List.of()));

        MvcResult result = mockMvc.perform(get("/api/games").param("active", "true"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void games_without_active_filter_is_rejected() throws Exception {
        mockMvc.perform(get("/api/games"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:joutio:bff:game:invalidListRequest"));
    }

    @Test
    void result_returns_the_composed_game_result() throws Exception {
        GameResultView view = new GameResultView(
                120,
                90,
                USER_ID,
                false,
                100,
                20,
                6,
                3,
                7,
                7,
                new GameResultView.RewardView(150, 30),
                new GameResultView.ProgressionResultView(818, 818, 1, true),
                new GameResultView.TopicMasteryView(250, 150, "FER", "BRONZE", 30, 250),
                18,
                true,
                200,
                -12,
                true,
                new TopicRefView("topic-1", java.util.Map.of(com.joutio.microservice.core.domain.model.i18n.Language.FR, "Culture générale"), "GENERAL", "🌍", "#ffffff", null, null),
                new UserRefView("opponent-1", "Bob", null),
                "opponent-1",
                null,
                false);
        when(gameViewService.result("game-1", USER_ID)).thenReturn(CompletableFuture.completedFuture(view));

        MvcResult mvcResult = mockMvc.perform(get("/api/games/game-1/result"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myScore").value(120))
                .andExpect(jsonPath("$.reward.xp").value(150))
                .andExpect(jsonPath("$.progression.rating").value(818))
                .andExpect(jsonPath("$.topicMastery.xp").value(250))
                .andExpect(jsonPath("$.opponentId").value("opponent-1"))
                .andExpect(jsonPath("$.topic.topicId").value("topic-1"));
        verify(gameViewService).result("game-1", USER_ID);
    }

    @Test
    void result_returns_forbidden_for_a_non_participant() throws Exception {
        when(gameViewService.result("game-1", USER_ID))
                .thenReturn(CompletableFuture.failedFuture(
                        new BffProblems.GameAccessDeniedProblem("game-1")));

        MvcResult mvcResult = mockMvc.perform(get("/api/games/game-1/result"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:joutio:bff:game:accessDenied"));
    }

    @Test
    void notifications_are_scoped_to_the_current_user() throws Exception {
        when(gameViewService.events("game-1", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        MvcResult mvcResult = mockMvc.perform(get("/api/games/game-1/notifications"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
        verify(gameViewService).events("game-1", USER_ID);
    }
}
