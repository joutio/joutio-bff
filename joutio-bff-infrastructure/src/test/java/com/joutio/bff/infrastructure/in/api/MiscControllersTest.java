package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.HomeService;
import com.joutio.bff.application.SuggestionService;
import com.joutio.bff.application.TopicViewService;
import com.joutio.bff.infrastructure.in.api.response.HomeView;
import com.joutio.bff.infrastructure.in.api.response.TopicCategoryView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MiscControllersTest {

    private static final String USER_ID = "user-1";

    private final HomeService homeService = mock(HomeService.class);
    private final SuggestionService suggestionService = mock(SuggestionService.class);
    private final TopicViewService topicViewService = mock(TopicViewService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ClockController(),
                new HomeController(homeService),
                new SuggestionController(suggestionService),
                new TopicCategoryController(topicViewService)).build();
        TestSecurity.authenticate(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    void clock_returns_server_time() throws Exception {
        mockMvc.perform(get("/api/clock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.epochMillis").isNumber());
    }

    @Test
    void home_returns_sections() throws Exception {
        when(homeService.home(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(new HomeView(List.of(), List.of(), List.of(), List.of())));

        MvcResult result = mockMvc.perform(get("/api/home"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followedTopics").isArray())
                .andExpect(jsonPath("$.trendingTopics").isArray())
                .andExpect(jsonPath("$.newTopics").isArray());
    }

    @Test
    void suggestions_return_list() throws Exception {
        when(suggestionService.suggest("pok", 5))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        MvcResult result = mockMvc.perform(get("/api/suggestions").param("q", "pok").param("limit", "5"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void topic_categories_return_labels() throws Exception {
        when(topicViewService.categories())
                .thenReturn(List.of(new TopicCategoryView("GAMES", "Jeux")));

        mockMvc.perform(get("/api/topic-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("GAMES"))
                .andExpect(jsonPath("$[0].label").value("Jeux"));
    }
}
