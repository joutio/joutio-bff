package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.MediaAdminService;
import com.joutio.bff.application.MediaOrigin;
import com.joutio.bff.infrastructure.in.api.problem.BffProblemExceptionHandler;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaDeleteResultView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaObjectView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaObjectsView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaOrphansView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaPurgeView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaStatsView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaUsageView;
import com.joutio.bff.infrastructure.in.api.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MediaAdminControllerTest {

    private static final String KEY = "uploads/a.jpg";

    private final MediaAdminService mediaAdminService = mock(MediaAdminService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MediaAdminController(mediaAdminService))
                .setControllerAdvice(new BffProblemExceptionHandler())
                .build();
    }

    @Test
    void objects_forwards_filters_and_returns_page() throws Exception {
        AdminMediaObjectView object = new AdminMediaObjectView(KEY, "UPLOAD", "https://media.test/" + KEY,
                100, Instant.parse("2026-10-13T08:00:00Z"), 1, List.of("user-1"), 2L);
        when(mediaAdminService.list(MediaOrigin.SEED, "cover", "cursor-1", 10))
                .thenReturn(CompletableFuture.completedFuture(new AdminMediaObjectsView(List.of(object), null)));

        MvcResult result = mockMvc.perform(get("/api/admin/media/objects")
                        .param("origin", "SEED")
                        .param("q", "cover")
                        .param("cursor", "cursor-1")
                        .param("size", "10"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objects[0].key").value(KEY))
                .andExpect(jsonPath("$.objects[0].referenceCount").value(2));
    }

    @Test
    void stats_returns_aggregates() throws Exception {
        when(mediaAdminService.stats()).thenReturn(new AdminMediaStatsView(
                List.of(new AdminMediaStatsView.BucketStats("UPLOAD", 2, 300)),
                new AdminMediaStatsView.AssetStats(2, 300, 1), 1, 50, 0, false));

        mockMvc.perform(get("/api/admin/media/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orphanObjectCount").value(1))
                .andExpect(jsonPath("$.assets.owners").value(1));
    }

    @Test
    void orphans_returns_both_directions() throws Exception {
        when(mediaAdminService.orphans()).thenReturn(new AdminMediaOrphansView(List.of(), List.of()));

        mockMvc.perform(get("/api/admin/media/orphans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objects").isArray())
                .andExpect(jsonPath("$.assets").isArray());
    }

    @Test
    void usage_returns_page() throws Exception {
        when(mediaAdminService.usage(0, 50))
                .thenReturn(PageResponse.of(List.of(new AdminMediaUsageView("user-1", 2, 300)), 0, 50, 1));

        mockMvc.perform(get("/api/admin/media/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].uploadedBy").value("user-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void delete_object_forwards_key_and_force() throws Exception {
        when(mediaAdminService.deleteObject(KEY, true))
                .thenReturn(CompletableFuture.completedFuture(new AdminMediaDeleteResultView(1, 2)));

        MvcResult result = mockMvc.perform(delete("/api/admin/media/objects")
                        .param("key", KEY)
                        .param("force", "true"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedObjects").value(1))
                .andExpect(jsonPath("$.deletedAssets").value(2));
    }

    @Test
    void delete_object_maps_referenced_conflict_to_409() throws Exception {
        when(mediaAdminService.deleteObject(KEY, false))
                .thenReturn(CompletableFuture.failedFuture(new BffProblems.MediaObjectReferencedProblem(KEY, 3)));

        MvcResult result = mockMvc.perform(delete("/api/admin/media/objects").param("key", KEY))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.context.references").value(3));
    }

    @Test
    void delete_asset_maps_unknown_to_404() throws Exception {
        UUID mediaId = UUID.randomUUID();
        when(mediaAdminService.deleteAsset(mediaId))
                .thenThrow(new BffProblems.MediaNotFoundProblem(mediaId.toString()));

        mockMvc.perform(delete("/api/admin/media/assets/{mediaId}", mediaId))
                .andExpect(status().isNotFound());
    }

    @Test
    void purge_orphans_returns_report() throws Exception {
        when(mediaAdminService.purgeOrphans())
                .thenReturn(CompletableFuture.completedFuture(new AdminMediaPurgeView(1, 1, 2)));

        MvcResult result = mockMvc.perform(post("/api/admin/media/purge-orphans"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skippedReferenced").value(2));
        verify(mediaAdminService).purgeOrphans();
    }
}
