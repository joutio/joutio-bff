package com.joutio.bff.infrastructure.in.api.response;

import java.util.List;

/**
 * Statistiques d'administration du stockage média : buckets S3 par origine, catalogue
 * {@code media_asset}, orphelins dans les deux sens (objet sans ligne / ligne sans objet).
 * {@code truncated} signale que le bucket dépasse la borne de scan (stats partielles).
 */
public record AdminMediaStatsView(
        List<BucketStats> buckets,
        AssetStats assets,
        long orphanObjectCount,
        long orphanObjectBytes,
        long danglingAssetCount,
        boolean truncated) {

    public record BucketStats(String origin, long count, long bytes) {
    }

    public record AssetStats(long count, long bytes, long owners) {
    }
}
