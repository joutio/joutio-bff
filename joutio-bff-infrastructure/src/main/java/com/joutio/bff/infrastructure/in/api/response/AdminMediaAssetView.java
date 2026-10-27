package com.joutio.bff.infrastructure.in.api.response;

import java.time.Instant;
import java.util.UUID;

/** Ligne {@code media_asset} sans objet correspondant dans le bucket (référence fantôme). */
public record AdminMediaAssetView(
        UUID id,
        String objectKey,
        String url,
        String uploadedBy,
        long sizeBytes,
        Instant createdAt) {
}
