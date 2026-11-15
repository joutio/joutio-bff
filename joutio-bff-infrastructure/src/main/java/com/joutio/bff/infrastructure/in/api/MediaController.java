package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.MediaRightsDeclaration;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.Instant;
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
    /** Déclaration de droits exigée (LCEN/DSA) ; désactivable le temps que les anciennes apps se mettent à jour. */
    private final boolean rightsDeclarationRequired;

    public MediaController(
            MediaService mediaService,
            @Value("${joutio.media.require-rights-declaration:true}") boolean rightsDeclarationRequired) {
        this.mediaService = mediaService;
        this.rightsDeclarationRequired = rightsDeclarationRequired;
    }

    /**
     * Envoi d'une image. {@code rightsDeclared=true} certifie que l'auteur détient les droits sur l'image (ou qu'elle
     * est libre de droits) selon les CGU {@code termsVersion} ; {@code sourceNote} (facultatif) indique l'auteur, la
     * licence ou la source. Sans déclaration : {@code 400 urn:joutio:bff:media:rightsNotDeclared}.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaView> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "rightsDeclared", required = false) Boolean rightsDeclared,
            @RequestParam(name = "termsVersion", required = false) String termsVersion,
            @RequestParam(name = "sourceNote", required = false) String sourceNote) {
        boolean declared = Boolean.TRUE.equals(rightsDeclared);
        if (!declared && rightsDeclarationRequired) {
            throw new BffProblems.MediaRightsNotDeclaredProblem();
        }
        MediaRightsDeclaration declaration = declared
                ? new MediaRightsDeclaration(Instant.now(), truncate(termsVersion, 32), truncate(sourceNote, 255))
                : null;
        MediaView view = mediaService.upload(SecurityHelper.getUserId(), file, declaration);
        return ResponseEntity.created(URI.create(view.url())).body(view);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    @DeleteMapping("/{mediaId}")
    public ResponseEntity<Void> delete(@PathVariable UUID mediaId) {
        mediaService.delete(SecurityHelper.getUserId(), mediaId);
        return ResponseEntity.noContent().build();
    }
}
