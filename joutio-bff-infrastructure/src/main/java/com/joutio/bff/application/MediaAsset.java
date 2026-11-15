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
        Instant createdAt,
        /** Déclaration de droits de l'auteur de l'envoi (LCEN/DSA, 2026-11-15) ; {@code null} avant cette date. */
        Instant rightsDeclaredAt,
        /** Version des CGU acceptée au moment de la déclaration. */
        String termsVersion,
        /** Source ou licence indiquée par l'auteur (facultatif). */
        String sourceNote) {

    /** Image sans déclaration de droits (envois antérieurs, seed, tests). */
    public MediaAsset(UUID id, String objectKey, String url, String contentType, long sizeBytes, int width, int height,
                      String sha256, String uploadedBy, Instant createdAt) {
        this(id, objectKey, url, contentType, sizeBytes, width, height, sha256, uploadedBy, createdAt, null, null, null);
    }
}
