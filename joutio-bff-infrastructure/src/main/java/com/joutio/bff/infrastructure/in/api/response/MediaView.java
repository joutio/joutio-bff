package com.joutio.bff.infrastructure.in.api.response;

import java.util.UUID;

/**
 * Référence publique d'une image stockée : URL immuable servie par l'Ingress `media.`
 * (bucket MinIO `joutio-media`). À attacher aux entités via les endpoints `image-url`/bannière.
 */
public record MediaView(
        UUID id,
        String url,
        String contentType,
        long sizeBytes,
        int width,
        int height) {
}
