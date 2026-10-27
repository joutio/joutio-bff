package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaDeleteResultView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaObjectsView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaPurgeView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaStatsView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaUsageView;
import com.joutio.bff.infrastructure.in.api.response.PageResponse;
import com.joutio.bff.infrastructure.out.media.MediaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MediaAdminServiceTest {

    private static final String PUBLIC_BASE = "https://media.example.test/joutio-media/";
    private static final String USER_ID = "user-1";

    private final MediaStorage storage = mock(MediaStorage.class);
    private final MediaAssetRepository repository = mock(MediaAssetRepository.class);
    private final MediaReferenceChecker referenceChecker = mock(MediaReferenceChecker.class);
    private final MediaAdminService service = new MediaAdminService(properties(), storage, repository, referenceChecker);

    @Test
    void list_enriches_objects_with_assets_and_reference_counts() {
        MediaObject upload = object("uploads/a.jpg", 100);
        when(storage.listObjects(MediaOrigin.UPLOAD.prefix(), null, 50))
                .thenReturn(new MediaObjectPage(List.of(upload), "cursor-1"));
        when(repository.findByObjectKeys(List.of(upload.objectKey())))
                .thenReturn(List.of(asset(upload.objectKey())));
        when(referenceChecker.countReferences(List.of(PUBLIC_BASE + upload.objectKey())))
                .thenReturn(CompletableFuture.completedFuture(Map.of(PUBLIC_BASE + upload.objectKey(), 2L)));

        AdminMediaObjectsView view = service.list(MediaOrigin.UPLOAD, null, null, 50).join();

        assertThat(view.nextCursor()).isEqualTo("cursor-1");
        assertThat(view.objects()).singleElement().satisfies(object -> {
            assertThat(object.key()).isEqualTo("uploads/a.jpg");
            assertThat(object.origin()).isEqualTo("UPLOAD");
            assertThat(object.url()).isEqualTo(PUBLIC_BASE + "uploads/a.jpg");
            assertThat(object.assetCount()).isEqualTo(1);
            assertThat(object.uploaders()).containsExactly(USER_ID);
            assertThat(object.referenceCount()).isEqualTo(2L);
        });
    }

    @Test
    void list_tolerates_reference_check_failure() {
        MediaObject upload = object("uploads/a.jpg", 100);
        when(storage.listObjects(null, null, 50))
                .thenReturn(new MediaObjectPage(List.of(upload), null));
        when(repository.findByObjectKeys(List.of(upload.objectKey()))).thenReturn(List.of());
        when(referenceChecker.countReferences(List.of(PUBLIC_BASE + upload.objectKey())))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("bus down")));

        AdminMediaObjectsView view = service.list(null, null, null, 50).join();

        assertThat(view.objects()).singleElement()
                .satisfies(object -> assertThat(object.referenceCount()).isNull());
    }

    @Test
    void delete_object_rejects_referenced_object_without_force() {
        String key = "uploads/a.jpg";
        when(repository.findByObjectKey(key)).thenReturn(List.of(asset(key), asset(key)));
        when(storage.exists(key)).thenReturn(true);
        when(referenceChecker.countReferences(List.of(PUBLIC_BASE + key)))
                .thenReturn(CompletableFuture.completedFuture(Map.of(PUBLIC_BASE + key, 3L)));

        assertThatThrownBy(() -> service.deleteObject(key, false).join())
                .hasCauseInstanceOf(BffProblems.MediaObjectReferencedProblem.class);
        verify(storage, never()).delete(key);
        verify(repository, never()).deleteByObjectKey(key);
    }

    @Test
    void delete_object_forced_deletes_object_and_all_asset_rows() {
        String key = "uploads/a.jpg";
        when(repository.findByObjectKey(key)).thenReturn(List.of(asset(key), asset(key)));
        when(storage.exists(key)).thenReturn(true);
        when(referenceChecker.countReferences(List.of(PUBLIC_BASE + key)))
                .thenReturn(CompletableFuture.completedFuture(Map.of(PUBLIC_BASE + key, 3L)));

        AdminMediaDeleteResultView result = service.deleteObject(key, true).join();

        assertThat(result.deletedObjects()).isEqualTo(1);
        assertThat(result.deletedAssets()).isEqualTo(2);
        verify(storage).delete(key);
        verify(repository).deleteByObjectKey(key);
    }

    @Test
    void delete_object_unreferenced_deletes_without_force() {
        String key = "uploads/a.jpg";
        when(repository.findByObjectKey(key)).thenReturn(List.of(asset(key)));
        when(storage.exists(key)).thenReturn(true);
        when(referenceChecker.countReferences(List.of(PUBLIC_BASE + key)))
                .thenReturn(CompletableFuture.completedFuture(Map.of(PUBLIC_BASE + key, 0L)));

        AdminMediaDeleteResultView result = service.deleteObject(key, false).join();

        assertThat(result.deletedObjects()).isEqualTo(1);
        assertThat(result.deletedAssets()).isEqualTo(1);
        verify(storage).delete(key);
        verify(repository).deleteByObjectKey(key);
    }

    @Test
    void delete_object_unknown_key_throws_not_found() {
        when(repository.findByObjectKey("uploads/gone.jpg")).thenReturn(List.of());
        when(storage.exists("uploads/gone.jpg")).thenReturn(false);

        assertThatThrownBy(() -> service.deleteObject("uploads/gone.jpg", false))
                .isInstanceOf(BffProblems.MediaObjectNotFoundProblem.class);
        verifyNoInteractions(referenceChecker);
    }

    @Test
    void delete_object_blank_key_throws_invalid_request() {
        assertThatThrownBy(() -> service.deleteObject("   ", false))
                .isInstanceOf(BffProblems.InvalidMediaAdminRequestProblem.class);
    }

    @Test
    void delete_asset_unknown_id_throws_not_found() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.deleteAsset(id))
                .isInstanceOf(BffProblems.MediaNotFoundProblem.class);
    }

    @Test
    void stats_detects_orphan_objects_and_dangling_assets() {
        MediaObject withAsset = object("uploads/a.jpg", 100);
        MediaObject orphan = object("uploads/orphan.jpg", 50);
        MediaObject seed = object("wikipedia/arts/x.jpg", 200);
        when(storage.listObjects(null, null, 1000))
                .thenReturn(new MediaObjectPage(List.of(withAsset, orphan, seed), null));
        when(repository.findAllOrdered())
                .thenReturn(List.of(asset(withAsset.objectKey()), asset("uploads/gone.jpg")));

        AdminMediaStatsView stats = service.stats();

        assertThat(stats.assets().count()).isEqualTo(2);
        assertThat(stats.assets().owners()).isEqualTo(1);
        assertThat(stats.orphanObjectCount()).isEqualTo(1);
        assertThat(stats.orphanObjectBytes()).isEqualTo(50);
        assertThat(stats.danglingAssetCount()).isEqualTo(1);
        assertThat(stats.truncated()).isFalse();
        assertThat(stats.buckets()).extracting(AdminMediaStatsView.BucketStats::origin)
                .containsExactly("SEED", "UPLOAD");
    }

    @Test
    void purge_deletes_orphan_objects_and_dangling_assets_but_keeps_referenced() {
        MediaObject referenced = object("uploads/referenced.jpg", 50);
        MediaObject free = object("uploads/free.jpg", 50);
        when(storage.listObjects(null, null, 1000))
                .thenReturn(new MediaObjectPage(List.of(referenced, free), null));
        MediaAsset dangling = asset("uploads/gone.jpg");
        when(repository.findAllOrdered()).thenReturn(List.of(dangling));
        when(referenceChecker.countReferences(List.of(PUBLIC_BASE + "uploads/referenced.jpg",
                PUBLIC_BASE + "uploads/free.jpg")))
                .thenReturn(CompletableFuture.completedFuture(Map.of(
                        PUBLIC_BASE + "uploads/referenced.jpg", 1L,
                        PUBLIC_BASE + "uploads/free.jpg", 0L)));

        AdminMediaPurgeView result = service.purgeOrphans().join();

        assertThat(result.deletedObjects()).isEqualTo(1);
        assertThat(result.deletedAssets()).isEqualTo(1);
        assertThat(result.skippedReferenced()).isEqualTo(1);
        verify(storage).delete("uploads/free.jpg");
        verify(storage, never()).delete("uploads/referenced.jpg");
        verify(repository).delete(dangling);
    }

    @Test
    void purge_fails_closed_when_reference_check_unavailable() {
        when(storage.listObjects(null, null, 1000))
                .thenReturn(new MediaObjectPage(List.of(object("uploads/orphan.jpg", 50)), null));
        when(repository.findAllOrdered()).thenReturn(List.of());
        when(referenceChecker.countReferences(List.of(PUBLIC_BASE + "uploads/orphan.jpg")))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("bus down")));

        assertThatThrownBy(() -> service.purgeOrphans().join())
                .hasCauseInstanceOf(BffProblems.MediaReferenceUnavailableProblem.class);
        verify(storage, never()).delete(anyString());
    }

    @Test
    void usage_maps_aggregates_with_pagination() {
        when(repository.countUploaders()).thenReturn(1L);
        when(repository.usageByUploader(0, 50)).thenReturn(List.of(new UploaderUsage(USER_ID, 2, 300)));

        PageResponse<AdminMediaUsageView> usage = service.usage(0, 50);

        assertThat(usage.totalElements()).isEqualTo(1);
        assertThat(usage.content()).singleElement().satisfies(view -> {
            assertThat(view.uploadedBy()).isEqualTo(USER_ID);
            assertThat(view.assetCount()).isEqualTo(2);
            assertThat(view.bytes()).isEqualTo(300);
        });
    }

    private static MediaProperties properties() {
        return new MediaProperties(
                PUBLIC_BASE,
                new MediaProperties.S3("http://localhost:9000", "us-east-1", "joutio-media", "key", "secret", true),
                DataSize.ofMegabytes(5),
                1600,
                8000,
                40_000_000L,
                0.85,
                200,
                DataSize.ofMegabytes(100));
    }

    private static MediaObject object(String key, long size) {
        return new MediaObject(key, size, Instant.parse("2026-10-13T08:00:00Z"), "etag-" + key);
    }

    private static MediaAsset asset(String objectKey) {
        return new MediaAsset(UUID.randomUUID(), objectKey, PUBLIC_BASE + objectKey, "image/jpeg",
                100, 100, 50, "sha", USER_ID, Instant.parse("2026-10-13T08:00:00Z"));
    }
}
