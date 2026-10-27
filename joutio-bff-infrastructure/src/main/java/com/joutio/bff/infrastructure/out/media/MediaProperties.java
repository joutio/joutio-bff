package com.joutio.bff.infrastructure.out.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Configuration média ({@code joutio.media.*}) : stockage objet S3/MinIO, URL publique des objets
 * et bornes d'upload/normalisation. En prod, l'endpoint et le bucket viennent du ConfigMap
 * (`joutio-bff-config`) et les clés du SealedSecret `joutio-bff-media`.
 */
@ConfigurationProperties(prefix = "joutio.media")
public record MediaProperties(
        /** Base publique des objets (Ingress `media.`) — l'URL stockée est `base + clé d'objet`. */
        @DefaultValue("") String publicBaseUrl,
        S3 s3,
        /** Taille maximale du fichier uploadé (avant normalisation). */
        @DefaultValue("5MB") DataSize maxFileSize,
        /** Dimension maximale de sortie (jamais d'upscale). */
        @DefaultValue("1600") int maxDimension,
        /** Dimension maximale acceptée en entrée (garde anti-décompression abusive). */
        @DefaultValue("8000") int maxInputDimension,
        /** Nombre de pixels maximal accepté en entrée. */
        @DefaultValue("40000000") long maxPixels,
        /** Qualité JPEG de sortie (0..1). */
        @DefaultValue("0.85") double jpegQuality,
        /** Quota : nombre d'images stockées par utilisateur. */
        @DefaultValue("200") long maxAssetsPerUser,
        /** Quota : octets normalisés stockés par utilisateur. */
        @DefaultValue("100MB") DataSize maxBytesPerUser) {

    public MediaProperties {
        if (s3 == null) {
            s3 = new S3(null, null, null, null, null, true);
        }
    }

    public record S3(
            String endpoint,
            String region,
            String bucket,
            String accessKey,
            String secretKey,
            /** MinIO : adressage par chemin (`path-style`) obligatoire hors DNS wildcard. */
            @DefaultValue("true") boolean pathStyle) {
    }
}
