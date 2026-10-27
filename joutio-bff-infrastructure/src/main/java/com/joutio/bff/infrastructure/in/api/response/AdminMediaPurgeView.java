package com.joutio.bff.infrastructure.in.api.response;

/**
 * Bilan d'une purge d'orphelins : objets S3 supprimés, lignes {@code media_asset} supprimées et
 * objets conservés car encore référencés par une entité métier.
 */
public record AdminMediaPurgeView(int deletedObjects, int deletedAssets, int skippedReferenced) {
}
