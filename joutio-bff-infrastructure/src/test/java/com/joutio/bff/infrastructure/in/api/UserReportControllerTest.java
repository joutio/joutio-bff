package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.UserReportService;
import com.joutio.bff.infrastructure.in.api.problem.BffProblemExceptionHandler;
import com.joutio.bff.infrastructure.in.api.response.UserReportAckView;
import com.joutio.identity.domain.model.UserReportStatus;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserReportControllerTest {

    private static final String USER_ID = "user-1";

    private final UserReportService service = mock(UserReportService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserReportController(service))
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
        when(service.report(eq("user-2"), eq(USER_ID), any()))
                .thenReturn(CompletableFuture.completedFuture(new UserReportAckView(
                        "report-1", UserReportStatus.OPEN, Instant.now(), true)));

        MvcResult result = mockMvc.perform(post("/api/profiles/user-2/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reasons":["HARASSMENT"],"comment":"Insultes"}
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
        when(service.report(eq("user-2"), eq(USER_ID), any()))
                .thenReturn(CompletableFuture.completedFuture(new UserReportAckView(
                        "report-1", UserReportStatus.OPEN, Instant.now(), false)));

        MvcResult result = mockMvc.perform(post("/api/profiles/user-2/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reasons":["SPAM"],"gameId":"game-1"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false));
    }

    @Test
    void report_requires_at_least_one_reason() throws Exception {
        mockMvc.perform(post("/api/profiles/user-2/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reasons":[]}
                                """))
                .andExpect(status().is4xxClientError());
    }
}
