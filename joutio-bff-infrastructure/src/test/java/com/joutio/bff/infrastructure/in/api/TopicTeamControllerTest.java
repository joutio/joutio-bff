package com.joutio.bff.infrastructure.in.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.joutio.bff.application.TopicTeamService;
import com.joutio.theme.domain.model.TopicTeamRole;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.concurrent.CompletableFuture;

class TopicTeamControllerTest {
    private final TopicTeamService service = mock(TopicTeamService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new TopicTeamController(service)).build();
        TestSecurity.authenticate("actor");
    }

    @AfterEach
    void clear() {
        TestSecurity.clear();
    }

    @Test
    void addMemberUsesAuthenticatedActor() throws Exception {
        when(service.set("topic", "actor", "writer", TopicTeamRole.WRITER))
                .thenReturn(
                        CompletableFuture.completedFuture(
                                new com.joutio.bff.infrastructure.in.api.response
                                        .TopicTeamMemberView(
                                        "writer", "Writer", null, TopicTeamRole.WRITER, false)));
        var result =
                mvc.perform(
                                put("/api/topics/topic/team/writer")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"role\":\"WRITER\"}"))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        verify(service).set("topic", "actor", "writer", TopicTeamRole.WRITER);
    }

    @Test
    void listTeam() throws Exception {
        when(service.team("topic", "actor"))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        var result = mvc.perform(get("/api/topics/topic/team")).andReturn();
        mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
}
