package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.live.LiveUpdate;
import com.joutio.bff.application.live.LiveViews;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LiveViewControllerTest {

    private final LiveViews liveViews = mock(LiveViews.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LiveViewController(liveViews)).build();
        TestSecurity.authenticate("user-1");
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    void reads_the_live_value_for_the_current_player() throws Exception {
        when(liveViews.read("score", "user-1", "user-1"))
                .thenReturn(CompletableFuture.completedFuture(
                        new LiveUpdate<>("score", "user-1", 7, Map.of("points", 120))));

        MvcResult result = mockMvc.perform(get("/api/live/score/user-1"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.view").value("score"))
                .andExpect(jsonPath("$.key").value("user-1"))
                .andExpect(jsonPath("$.version").value(7))
                .andExpect(jsonPath("$.payload.points").value(120));
    }
}
