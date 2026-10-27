package com.joutio.bff.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance des images uploadées (table {@code media_asset}, BFF). */
public interface MediaAssetRepository {

    MediaAsset save(MediaAsset asset);

    Optional<MediaAsset> findById(UUID id);

    /** Déduplication : un même contenu (sha256) n'est stocké qu'une fois par utilisateur. */
    Optional<MediaAsset> findBySha256AndUploadedBy(String sha256, String uploadedBy);

    long countByUploadedBy(String uploadedBy);

    /** Références possédées par un joueur (suppression de compte). */
    List<MediaAsset> findByUploadedBy(String uploadedBy);

    long sumBytesByUploadedBy(String uploadedBy);

    long countByObjectKey(String objectKey);

    void delete(MediaAsset asset);

    /** Toutes les lignes partageant une clé d'objet (déduplication inter-utilisateurs). */
    List<MediaAsset> findByObjectKey(String objectKey);

    /** Résolution batch par clés d'objet (une même clé peut porter plusieurs lignes). */
    List<MediaAsset> findByObjectKeys(Collection<String> objectKeys);

    /** Catalogue complet, du plus ancien au plus récent (stats et détection d'orphelins). */
    List<MediaAsset> findAllOrdered();

    /** Usage agrégé par joueur, trié par octets décroissants. */
    List<UploaderUsage> usageByUploader(int page, int size);

    /** Nombre de joueurs possédant au moins une image. */
    long countUploaders();

    /** Suppression de toutes les lignes d'une clé d'objet (purge d'administration). */
    long deleteByObjectKey(String objectKey);
}
