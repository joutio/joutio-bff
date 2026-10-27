package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.TopicAuthoringService;
import com.joutio.bff.infrastructure.in.api.problem.BffProblemExceptionHandler;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuestionControllerTest {

    private static final String USER_ID = "user-1";

    private final TopicAuthoringService topicAuthoringService = mock(TopicAuthoringService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new QuestionController(topicAuthoringService))
                .setControllerAdvice(new BffProblemExceptionHandler())
                .build();
        TestSecurity.authenticate(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    void update_text_returns_204() throws Exception {
        when(topicAuthoringService.updateQuestionText(eq("question-1"), eq(USER_ID), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(put("/api/questions/question-1/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"fr\",\"text\":\"Capitale de la France ?\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent());
    }

    @Test
    void update_text_by_non_owner_returns_403() throws Exception {
        when(topicAuthoringService.updateQuestionText(eq("question-1"), eq(USER_ID), any()))
                .thenReturn(CompletableFuture.failedFuture(new BffProblems.NotTopicOwnerProblem("topic-1")));

        MvcResult result = mockMvc.perform(put("/api/questions/question-1/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"fr\",\"text\":\"Capitale de la France ?\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.category").value("PERMISSION"));
    }

    @Test
    void update_answers_requires_four_choices() throws Exception {
        mockMvc.perform(put("/api/questions/question-1/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "language": "fr",
                                  "answers": [
                                    {"choice": "A", "text": "Paris"},
                                    {"choice": "B", "text": "Lyon"}
                                  ]
                                }
                                """))
                .andExpect(status().is4xxClientError());
    }
}
