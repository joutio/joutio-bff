package com.joutio.bff.application;

import java.time.Instant;

/**
 * Objet présent dans le stockage média (listing d'administration) : clé, taille, date de
 * dernière modification et ETag S3.
 */
public record MediaObject(String objectKey, long sizeBytes, Instant lastModified, String etag) {
}
