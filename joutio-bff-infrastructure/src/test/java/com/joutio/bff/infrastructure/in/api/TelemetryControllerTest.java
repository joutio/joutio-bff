package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.TelemetryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TelemetryControllerTest {

    private static final String USER_ID = "user-1";

    private final TelemetryService telemetryService = mock(TelemetryService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TelemetryController(telemetryService)).build();
        TestSecurity.authenticate(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    void accepts_product_event_without_echoing_the_user() throws Exception {
        when(telemetryService.recordEvent(USER_ID, "duel_start_requested",
                Map.of("mode", "public", "first", "true")))
                .thenReturn(TelemetryService.RecordResult.ACCEPTED);

        mockMvc.perform(post("/api/telemetry/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"duel_start_requested","properties":{"mode":"public","first":"true"}}
                                """))
                .andExpect(status().isAccepted());

        verify(telemetryService).recordEvent(USER_ID, "duel_start_requested",
                Map.of("mode", "public", "first", "true"));
    }

    @Test
    void rejects_unknown_product_event() throws Exception {
        when(telemetryService.recordEvent(USER_ID, "unknown", Map.of()))
                .thenReturn(TelemetryService.RecordResult.INVALID);

        mockMvc.perform(post("/api/telemetry/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"unknown"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns_429_when_rate_limited() throws Exception {
        when(telemetryService.recordEvent(USER_ID, "play_mode_opened", Map.of()))
                .thenReturn(TelemetryService.RecordResult.RATE_LIMITED);

        mockMvc.perform(post("/api/telemetry/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"play_mode_opened"}
                                """))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void accepts_client_error_without_echoing_the_user() throws Exception {
        when(telemetryService.recordClientError(
                USER_ID, "JS_ERROR", "boom", "stack", "/topics/{id}", "build-1", "window.error"))
                .thenReturn(TelemetryService.RecordResult.ACCEPTED);

        mockMvc.perform(post("/api/telemetry/client-errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"JS_ERROR","message":"boom","stack":"stack","route":"/topics/{id}","buildId":"build-1","source":"window.error"}
                                """))
                .andExpect(status().isAccepted());

        verify(telemetryService).recordClientError(
                USER_ID, "JS_ERROR", "boom", "stack", "/topics/{id}", "build-1", "window.error");
    }

    @Test
    void rejects_blank_client_error_message() throws Exception {
        mockMvc.perform(post("/api/telemetry/client-errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"JS_ERROR","message":""}
                                """))
                .andExpect(status().isBadRequest());
    }
}
