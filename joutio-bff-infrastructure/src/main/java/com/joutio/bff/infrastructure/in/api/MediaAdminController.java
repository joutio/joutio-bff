package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.MediaAdminService;
import com.joutio.bff.application.MediaOrigin;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaDeleteResultView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaObjectsView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaOrphansView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaPurgeView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaStatsView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaUsageView;
import com.joutio.bff.infrastructure.in.api.response.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /api/admin/media} — console d'administration du stockage média (bucket MinIO
 * {@code joutio-media}) : listing des objets (uploads BFF + images du seed), statistiques, usage
 * par joueur, orphelins S3 ↔ {@code media_asset}, suppression et purge. Surface réservée au rôle
 * {@code ROLE_ADMIN} (claim {@code roles} du JWT identity) ; toute suppression d'objet référencé
 * exige {@code force=true}.
 */
@RestController
@RequestMapping("/api/admin/media")
@PreAuthorize("hasRole('ADMIN')")
public class MediaAdminController {

    private final MediaAdminService mediaAdminService;

    public MediaAdminController(MediaAdminService mediaAdminService) {
        this.mediaAdminService = mediaAdminService;
    }

    @GetMapping("/objects")
    public CompletableFuture<ResponseEntity<AdminMediaObjectsView>> objects(
            @RequestParam(required = false) MediaOrigin origin,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int size) {
        return mediaAdminService.list(origin, q, cursor, size).thenApply(ResponseEntity::ok);
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminMediaStatsView> stats() {
        return ResponseEntity.ok(mediaAdminService.stats());
    }

    @GetMapping("/orphans")
    public ResponseEntity<AdminMediaOrphansView> orphans() {
        return ResponseEntity.ok(mediaAdminService.orphans());
    }

    @GetMapping("/usage")
    public ResponseEntity<PageResponse<AdminMediaUsageView>> usage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(mediaAdminService.usage(page, size));
    }

    @DeleteMapping("/objects")
    public CompletableFuture<ResponseEntity<AdminMediaDeleteResultView>> deleteObject(
            @RequestParam String key,
            @RequestParam(defaultValue = "false") boolean force) {
        return mediaAdminService.deleteObject(key, force).thenApply(ResponseEntity::ok);
    }

    @DeleteMapping("/assets/{mediaId}")
    public ResponseEntity<AdminMediaDeleteResultView> deleteAsset(@PathVariable UUID mediaId) {
        return ResponseEntity.ok(mediaAdminService.deleteAsset(mediaId));
    }

    @PostMapping("/purge-orphans")
    public CompletableFuture<ResponseEntity<AdminMediaPurgeView>> purgeOrphans() {
        return mediaAdminService.purgeOrphans().thenApply(ResponseEntity::ok);
    }
}
