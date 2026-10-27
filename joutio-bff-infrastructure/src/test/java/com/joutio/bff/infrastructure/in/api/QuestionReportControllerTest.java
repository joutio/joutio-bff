package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.QuestionReportService;
import com.joutio.bff.infrastructure.in.api.problem.BffProblemExceptionHandler;
import com.joutio.bff.infrastructure.in.api.response.AdminQuestionReportResolutionView;
import com.joutio.bff.infrastructure.in.api.response.OwnerQuestionReportView;
import com.joutio.bff.infrastructure.in.api.response.QuestionReportAckView;
import com.joutio.theme.domain.model.QuestionReportStatus;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuestionReportControllerTest {

    private static final String USER_ID = "user-1";

    private final QuestionReportService service = mock(QuestionReportService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new QuestionReportController(service))
                .setControllerAdvice(new BffProblemExceptionHandler())
                .build();
        TestSecurity.authenticate(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    void report_returns_201_when_created() throws Exception {
        when(service.report(eq("question-1"), eq(USER_ID), any()))
                .thenReturn(CompletableFuture.completedFuture(new QuestionReportAckView(
                        "report-1", QuestionReportStatus.OPEN, Instant.now(), true)));

        MvcResult result = mockMvc.perform(post("/api/questions/question-1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId":"game-1","reasons":["ANSWERS"],"comment":"Bonne réponse fausse"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportId").value("report-1"))
                .andExpect(jsonPath("$.created").value(true));
    }

    @Test
    void report_returns_200_when_already_open() throws Exception {
        when(service.report(eq("question-1"), eq(USER_ID), any()))
                .thenReturn(CompletableFuture.completedFuture(new QuestionReportAckView(
                        "report-1", QuestionReportStatus.OPEN, Instant.now(), false)));

        MvcResult result = mockMvc.perform(post("/api/questions/question-1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId":"game-1","reasons":["IMAGE"]}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false));
    }

    @Test
    void report_requires_at_least_one_reason() throws Exception {
        mockMvc.perform(post("/api/questions/question-1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId":"game-1","reasons":[]}
                                """))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void owner_reports_returns_list() throws Exception {
        when(service.ownerReports("question-1", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(List.of(
                        new OwnerQuestionReportView(
                                "report-1", List.of(), null, null, null,
                                QuestionReportStatus.OPEN, Instant.now(), null, null))));

        MvcResult result = mockMvc.perform(get("/api/questions/question-1/reports"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reportId").value("report-1"));
    }

    @Test
    void resolve_all_returns_count() throws Exception {
        when(service.resolveOwnerAll(eq("question-1"), eq(USER_ID), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new AdminQuestionReportResolutionView(2)));

        MvcResult result = mockMvc.perform(post("/api/questions/question-1/reports/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resolution":"RESOLVED","note":"Corrigé"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedCount").value(2));
    }
}
