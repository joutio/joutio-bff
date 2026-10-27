package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.MediaService;
import com.joutio.bff.infrastructure.in.api.response.MediaView;
import com.joutio.microservice.security.SecurityHelper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

/**
 * {@code /api/media} — upload d'images (JPEG/PNG, ≤ 5 Mo par défaut) vers le stockage objet
 * MinIO, normalisées et référencées par URL publique immuable. L'URL retournée est ensuite
 * attachée aux entités via les endpoints existants (`PUT .../image-url`, bannière de sujet/profil).
 * Suppression : propriétaire de l'image uniquement.
 */
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaView> upload(@RequestPart("file") MultipartFile file) {
        MediaView view = mediaService.upload(SecurityHelper.getUserId(), file);
        return ResponseEntity.created(URI.create(view.url())).body(view);
    }

    @DeleteMapping("/{mediaId}")
    public ResponseEntity<Void> delete(@PathVariable UUID mediaId) {
        mediaService.delete(SecurityHelper.getUserId(), mediaId);
        return ResponseEntity.noContent().build();
    }
}
