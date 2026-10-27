package com.joutio.bff.infrastructure.in.api.response;

/** Bilan d'une suppression/purge d'administration : objets S3 et lignes {@code media_asset}. */
public record AdminMediaDeleteResultView(int deletedObjects, int deletedAssets) {
}
