package com.joutio.bff.infrastructure.in.api.response;

/** Usage du stockage média d'un joueur (nombre d'images et octets normalisés). */
public record AdminMediaUsageView(String uploadedBy, long assetCount, long bytes) {
}
