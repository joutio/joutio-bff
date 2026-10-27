package com.joutio.bff.infrastructure.in.api.response;

import java.time.Instant;
import java.util.List;

/**
 * Vue d'administration d'un objet du bucket : origine (upload/seed), URL publique, volumétrie et
 * enrichissements (lignes {@code media_asset}, références métier) — {@code referenceCount}
 * {@code null} si la vérification inter-services est indisponible (le listing reste servi).
 */
public record AdminMediaObjectView(
        String key,
        String origin,
        String url,
        long sizeBytes,
        Instant lastModified,
        int assetCount,
        List<String> uploaders,
        Long referenceCount) {
}
