package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.UserModerationAdminService;
import com.joutio.bff.application.UserReportService;
import com.joutio.bff.infrastructure.in.api.problem.BffProblemExceptionHandler;
import com.joutio.bff.infrastructure.in.api.response.AdminReportedUserSummaryView;
import com.joutio.bff.infrastructure.in.api.response.AdminUserAccountView;
import com.joutio.bff.infrastructure.in.api.response.AdminUserCatalogueView;
import com.joutio.bff.infrastructure.in.api.response.AdminUserDetailView;
import com.joutio.bff.infrastructure.in.api.response.AdminUserModerationSummaryView;
import com.joutio.bff.infrastructure.in.api.response.AdminUserModerationView;
import com.joutio.bff.infrastructure.in.api.response.AdminUserProfileView;
import com.joutio.bff.infrastructure.in.api.response.PageResponse;
import com.joutio.identity.domain.model.AccountStatus;
import com.joutio.identity.domain.model.UserSuspensionReason;
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

class UserModerationAdminControllerTest {

    private final UserModerationAdminService adminService = mock(UserModerationAdminService.class);
    private final UserReportService reportService = mock(UserReportService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserModerationAdminController(adminService, reportService))
                .setControllerAdvice(new BffProblemExceptionHandler())
                .build();
        TestSecurity.authenticate("admin-1");
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    void summary_returns_counts() throws Exception {
        when(adminService.summary()).thenReturn(CompletableFuture.completedFuture(
                new AdminUserModerationSummaryView(4, 2, 1)));

        MvcResult result = mockMvc.perform(get("/api/admin/user-reports/summary"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openReports").value(4))
                .andExpect(jsonPath("$.suspendedUsers").value(1));
    }

    @Test
    void queue_returns_reported_users() throws Exception {
        when(adminService.queue(any(), any(), eq(0), eq(20)))
                .thenReturn(CompletableFuture.completedFuture(PageResponse.of(List.of(
                        new AdminReportedUserSummaryView(
                                "user-2", "user2@joutio.dev", "Bravo", null,
                                false, null, 2, 3, Instant.now())), 0, 20, 1)));

        MvcResult result = mockMvc.perform(get("/api/admin/user-reports"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].pseudonym").value("Bravo"));
    }

    @Test
    void catalogue_returns_paged_players() throws Exception {
        when(adminService.catalogue(eq("brav"), eq(AccountStatus.ACTIVE), eq(true), eq(0), eq(20)))
                .thenReturn(CompletableFuture.completedFuture(PageResponse.of(List.of(
                        new AdminUserCatalogueView(
                                "user-2", "user2@joutio.dev", "Bravo", null,
                                AccountStatus.ACTIVE, false, null, Instant.now(), false,
                                0, 0, null)), 0, 20, 1)));

        MvcResult result = mockMvc.perform(get("/api/admin/users")
                        .param("q", "brav")
                        .param("status", "ACTIVE")
                        .param("reportedOnly", "true"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].pseudonym").value("Bravo"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void detail_returns_account_and_reports() throws Exception {
        when(adminService.detail("user-2")).thenReturn(CompletableFuture.completedFuture(
                new AdminUserDetailView(
                        new AdminUserAccountView(
                                "user-2", "user2@joutio.dev", Instant.now(),
                                AccountStatus.ACTIVE, false, null, null, null,
                                null, null, null, null, null, null, false),
                        new AdminUserProfileView("Bravo", null, "FR", null, null),
                        List.of())));

        MvcResult result = mockMvc.perform(get("/api/admin/users/user-2"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.email").value("user2@joutio.dev"))
                .andExpect(jsonPath("$.profile.pseudonym").value("Bravo"));
    }

    @Test
    void suspend_delegates_to_service() throws Exception {
        when(adminService.suspend(eq("user-2"), eq("admin-1"), any()))
                .thenReturn(CompletableFuture.completedFuture(new AdminUserModerationView(
                        "user-2", AccountStatus.SUSPENDED, Instant.now(),
                        UserSuspensionReason.HARASSMENT, 2, false)));

        MvcResult result = mockMvc.perform(post("/api/admin/users/user-2/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"HARASSMENT","duration":"P7D","permanent":false,"resolveOpenReports":true}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.resolvedReports").value(2));
    }
}
