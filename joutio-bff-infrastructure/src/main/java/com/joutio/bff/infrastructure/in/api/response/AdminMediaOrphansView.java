package com.joutio.bff.infrastructure.in.api.response;

import java.util.List;

/** Orphelins du stockage : objets S3 sans ligne {@code media_asset} et lignes sans objet S3. */
public record AdminMediaOrphansView(
        List<AdminMediaObjectView> objects,
        List<AdminMediaAssetView> assets) {
}
