package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.ProfileViewService;
import com.joutio.bff.infrastructure.in.api.response.PresenceView;
import com.joutio.profile.domain.model.PresenceStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PresenceControllerTest {

    private final ProfileViewService profileViewService = mock(ProfileViewService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PresenceController(profileViewService)).build();
        TestSecurity.authenticate("user-1");
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    void unknown_player_returns_not_found() throws Exception {
        when(profileViewService.presence("user-2"))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        MvcResult result = mockMvc.perform(get("/api/presence/user-2"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isNotFound());
    }

    @Test
    void known_player_returns_presence() throws Exception {
        when(profileViewService.presence("user-2"))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(
                        new PresenceView("user-2", PresenceStatus.ONLINE, Instant.parse("2026-09-27T10:00:00Z")))));

        MvcResult result = mockMvc.perform(get("/api/presence/user-2"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ONLINE"));
    }
}
