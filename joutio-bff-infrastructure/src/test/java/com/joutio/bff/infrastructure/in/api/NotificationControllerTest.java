package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.NotificationViewService;
import com.joutio.bff.infrastructure.in.api.response.PageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerTest {

    private static final String USER_ID = "user-1";

    private final NotificationViewService notificationViewService = mock(NotificationViewService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationController(notificationViewService))
                .build();
        TestSecurity.authenticate(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    void list_delegates_with_pagination() throws Exception {
        when(notificationViewService.list(USER_ID, true, 0, 20))
                .thenReturn(CompletableFuture.completedFuture(PageResponse.of(List.of(), 0, 20, 0)));

        MvcResult result = mockMvc.perform(get("/api/notifications").param("unreadOnly", "true"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

        verify(notificationViewService).list(USER_ID, true, 0, 20);
    }

    @Test
    void unread_count_delegates() throws Exception {
        when(notificationViewService.unreadCount(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(3L));

        MvcResult result = mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
    }

    @Test
    void mark_read_and_read_all_delegate() throws Exception {
        when(notificationViewService.markRead(USER_ID, "notif-1"))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(notificationViewService.markAllRead(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult read = mockMvc.perform(post("/api/notifications/notif-1/read"))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(read)).andExpect(status().isOk());

        MvcResult readAll = mockMvc.perform(post("/api/notifications/read-all"))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(readAll)).andExpect(status().isOk());

        verify(notificationViewService).markRead(USER_ID, "notif-1");
        verify(notificationViewService).markAllRead(USER_ID);
    }

    @Test
    void mark_unread_delegates() throws Exception {
        when(notificationViewService.markUnread(USER_ID, "notif-1"))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(post("/api/notifications/notif-1/unread"))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

        verify(notificationViewService).markUnread(USER_ID, "notif-1");
    }

    @Test
    void delete_delegates_and_returns_no_content() throws Exception {
        when(notificationViewService.delete(USER_ID, "notif-1"))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(delete("/api/notifications/notif-1"))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent());

        verify(notificationViewService).delete(USER_ID, "notif-1");
    }

    @Test
    void delete_all_delegates_and_returns_no_content() throws Exception {
        when(notificationViewService.deleteAll(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(delete("/api/notifications"))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent());

        verify(notificationViewService).deleteAll(USER_ID);
    }
}
