package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.MediaService;
import com.joutio.bff.infrastructure.in.api.problem.BffProblemExceptionHandler;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.response.MediaView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MediaControllerTest {

    private static final String USER_ID = "user-1";
    private static final String URL = "https://media.example.test/joutio-media/uploads/abc.jpg";

    private final MediaService mediaService = mock(MediaService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TestSecurity.authenticate(USER_ID);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MediaController(mediaService))
                .setControllerAdvice(new BffProblemExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    void upload_returns_created_media_view() throws Exception {
        when(mediaService.upload(eq(USER_ID), any()))
                .thenReturn(new MediaView(UUID.randomUUID(), URL, "image/jpeg", 1234, 100, 50));

        mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1, 2, 3})))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", URL))
                .andExpect(jsonPath("$.url").value(URL))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.width").value(100))
                .andExpect(jsonPath("$.height").value(50));
    }

    @Test
    void upload_maps_too_large_to_413() throws Exception {
        when(mediaService.upload(eq(USER_ID), any()))
                .thenThrow(new BffProblems.MediaTooLargeProblem("trop gros"));

        mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1})))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void upload_maps_quota_to_429() throws Exception {
        when(mediaService.upload(eq(USER_ID), any()))
                .thenThrow(new BffProblems.MediaQuotaExceededProblem("quota"));

        mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1})))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void upload_maps_storage_failure_to_503() throws Exception {
        when(mediaService.upload(eq(USER_ID), any()))
                .thenThrow(new BffProblems.MediaStorageUnavailableProblem("stockage"));

        mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1})))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void upload_maps_unsupported_format_to_400() throws Exception {
        when(mediaService.upload(eq(USER_ID), any()))
                .thenThrow(new BffProblems.MediaUnsupportedFormatProblem());

        mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "cover.svg", "image/svg+xml", new byte[]{1})))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returns_no_content() throws Exception {
        UUID mediaId = UUID.randomUUID();

        mockMvc.perform(delete("/api/media/{mediaId}", mediaId))
                .andExpect(status().isNoContent());

        verify(mediaService).delete(USER_ID, mediaId);
    }

    @Test
    void delete_maps_not_owner_to_403() throws Exception {
        UUID mediaId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new BffProblems.MediaNotOwnerProblem(mediaId.toString()))
                .when(mediaService).delete(USER_ID, mediaId);

        mockMvc.perform(delete("/api/media/{mediaId}", mediaId))
                .andExpect(status().isForbidden());
    }
}
