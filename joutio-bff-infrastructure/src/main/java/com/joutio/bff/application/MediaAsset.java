package com.joutio.bff.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Image persistée dans le stockage objet (bucket MinIO `joutio-media`). La clé d'objet est
 * dérivée du sha256 du contenu normalisé (déduplication) ; l'URL publique est stable et immuable.
 */
public record MediaAsset(
        UUID id,
        String objectKey,
        String url,
        String contentType,
        long sizeBytes,
        int width,
        int height,
        String sha256,
        String uploadedBy,
        Instant createdAt) {
}
