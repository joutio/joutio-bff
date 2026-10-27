package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.QuestionAdminService;
import com.joutio.bff.application.QuestionReportService;
import com.joutio.bff.infrastructure.in.api.problem.BffProblemExceptionHandler;
import com.joutio.bff.infrastructure.in.api.response.AdminQuestionReportSummaryView;
import com.joutio.bff.infrastructure.in.api.response.PageResponse;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuestionAdminControllerTest {

    private static final String ADMIN_ID = "admin-1";

    private final QuestionAdminService adminService = mock(QuestionAdminService.class);
    private final QuestionReportService reportService = mock(QuestionReportService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new QuestionAdminController(adminService, reportService))
                .setControllerAdvice(new BffProblemExceptionHandler())
                .build();
        TestSecurity.authenticate(ADMIN_ID);
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    void queue_returns_empty_page() throws Exception {
        when(adminService.queue(any(), any(), eq(0), eq(20)))
                .thenReturn(CompletableFuture.completedFuture(
                        PageResponse.of(List.of(), 0, 20, 0)));

        MvcResult result = mockMvc.perform(get("/api/admin/question-reports"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void summary_returns_counts() throws Exception {
        when(adminService.summary())
                .thenReturn(CompletableFuture.completedFuture(
                        new AdminQuestionReportSummaryView(3, 2, 1, 1)));

        MvcResult result = mockMvc.perform(get("/api/admin/question-reports/summary"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openReports").value(3))
                .andExpect(jsonPath("$.suspendedQuestions").value(1));
    }

    @Test
    void create_question_returns_created_with_location() throws Exception {
        when(adminService.create(eq("topic-1"), eq(ADMIN_ID), any()))
                .thenReturn(CompletableFuture.completedFuture("question-1"));

        MvcResult result = mockMvc.perform(post("/api/admin/topics/topic-1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contents": [{
                                    "language": "fr",
                                    "text": "Capitale de la France ?",
                                    "answers": [
                                      {"choice":"A","text":"Paris"},
                                      {"choice":"B","text":"Lyon"},
                                      {"choice":"C","text":"Marseille"},
                                      {"choice":"D","text":"Lille"}
                                    ]
                                  }],
                                  "correctAnswer": "A"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/admin/questions/question-1"))
                .andExpect(jsonPath("$.questionId").value("question-1"));
    }

    @Test
    void suspend_returns_ok() throws Exception {
        when(adminService.suspend(eq("question-1"), eq(ADMIN_ID), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(post("/api/admin/questions/question-1/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"Contenu douteux"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
    }
}
