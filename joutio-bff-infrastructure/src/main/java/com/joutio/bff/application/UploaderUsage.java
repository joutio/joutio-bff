package com.joutio.bff.application;

/**
 * Usage du stockage média par joueur (volumétrie du quota) — agrégat de {@code media_asset}.
 */
public record UploaderUsage(String uploadedBy, long assetCount, long totalBytes) {
}
