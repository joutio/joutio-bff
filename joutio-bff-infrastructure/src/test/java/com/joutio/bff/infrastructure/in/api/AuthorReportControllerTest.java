package com.joutio.bff.infrastructure.in.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.joutio.bff.application.QuestionReportService;

import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.CompletableFuture;

class AuthorReportControllerTest {
    @Test
    void detailIsResolvedUsingAuthenticatedUser() throws Exception {
        var service = mock(QuestionReportService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AuthorReportController(service)).build();
        TestSecurity.authenticate("actor");
        try {
            when(service.ownerReport("report", "actor"))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    new QuestionReportService.ReportDetail(
                                            "topic", "question", null)));
            var result = mvc.perform(get("/api/question-reports/report")).andReturn();
            mvc.perform(asyncDispatch(result))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.topicId").value("topic"));
            verify(service).ownerReport("report", "actor");
        } finally {
            TestSecurity.clear();
        }
    }
}
