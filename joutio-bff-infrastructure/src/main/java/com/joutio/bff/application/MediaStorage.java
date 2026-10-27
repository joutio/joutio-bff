package com.joutio.bff.application;

/**
 * Port de stockage objet (MinIO/S3-compatible). Les objets sont immuables : même clé = même
 * contenu (clé dérivée du sha256), `Cache-Control` long.
 */
public interface MediaStorage {

    void put(String objectKey, byte[] content, String contentType, String sha256);

    void delete(String objectKey);

    /** Liste une page d'objets ({@code prefix} nullable = tout le bucket, {@code maxKeys} borné). */
    MediaObjectPage listObjects(String prefix, String continuationToken, int maxKeys);

    /** Indique si l'objet existe encore dans le bucket (détection de références fantômes). */
    boolean exists(String objectKey);
}
