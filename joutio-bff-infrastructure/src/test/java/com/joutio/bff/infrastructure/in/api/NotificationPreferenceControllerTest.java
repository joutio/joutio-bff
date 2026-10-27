package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.NotificationViewService;
import com.joutio.notification.domain.model.NotificationCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationPreferenceControllerTest {

    private static final String USER_ID = "user-1";

    private final NotificationViewService notificationViewService = mock(NotificationViewService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationPreferenceController(notificationViewService))
                .build();
        TestSecurity.authenticate(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    void list_delegates() throws Exception {
        when(notificationViewService.preferences(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        MvcResult result = mockMvc.perform(get("/api/notification-preferences"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
    }

    @Test
    void update_delegates_with_category() throws Exception {
        when(notificationViewService.updatePreference(USER_ID, NotificationCategory.ROOM, false))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(put("/api/notification-preferences/ROOM")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":false}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent());

        verify(notificationViewService).updatePreference(USER_ID, NotificationCategory.ROOM, false);
    }
}
