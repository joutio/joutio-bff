package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.response.MediaView;
import com.joutio.bff.infrastructure.out.media.MediaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Upload d'images de la façade : validation/normalisation, déduplication par sha256, quota par
 * utilisateur, dépôt dans le stockage objet puis persistance de la référence ({@code media_asset}).
 * L'URL publique retournée est ensuite attachée aux entités métier via les endpoints existants
 * (`PUT .../image-url`, bannière).
 */
@Service
public class MediaService {

    private static final Logger logger = LoggerFactory.getLogger(MediaService.class);

    private final MediaProperties properties;
    private final MediaImageNormalizer normalizer;
    private final MediaStorage storage;
    private final MediaAssetRepository repository;

    public MediaService(MediaProperties properties,
                        MediaImageNormalizer normalizer,
                        MediaStorage storage,
                        MediaAssetRepository repository) {
        this.properties = properties;
        this.normalizer = normalizer;
        this.storage = storage;
        this.repository = repository;
    }

    public MediaView upload(String userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BffProblems.MediaFileMissingProblem();
        }
        if (file.getSize() > properties.maxFileSize().toBytes()) {
            throw new BffProblems.MediaTooLargeProblem(
                    "fichier de " + file.getSize() + " octets (max " + properties.maxFileSize() + ")");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BffProblems.MediaInvalidImageProblem("fichier illisible");
        }

        MediaImage image = normalizer.normalize(content);
        String sha256 = sha256(image.content());
        Optional<MediaAsset> existing = repository.findBySha256AndUploadedBy(sha256, userId);
        if (existing.isPresent()) {
            return toView(existing.get());
        }
        long bytes = image.content().length;
        if (repository.countByUploadedBy(userId) >= properties.maxAssetsPerUser()
                || repository.sumBytesByUploadedBy(userId) + bytes > properties.maxBytesPerUser().toBytes()) {
            throw new BffProblems.MediaQuotaExceededProblem(
                    "quota d'images atteint (max " + properties.maxAssetsPerUser() + " images / "
                            + properties.maxBytesPerUser() + ")");
        }

        String objectKey = "uploads/" + sha256.substring(0, 16) + image.extension();
        storage.put(objectKey, image.content(), image.contentType(), sha256);
        MediaAsset asset = new MediaAsset(UUID.randomUUID(), objectKey, urlFor(objectKey),
                image.contentType(), bytes, image.width(), image.height(), sha256, userId, Instant.now());
        repository.save(asset);
        logger.info("Image uploadée par {} : {} ({} octets, {}x{})", userId, objectKey, bytes,
                image.width(), image.height());
        return toView(asset);
    }

    public void delete(String userId, UUID mediaId) {
        MediaAsset asset = repository.findById(mediaId)
                .orElseThrow(() -> new BffProblems.MediaNotFoundProblem(mediaId.toString()));
        if (!asset.uploadedBy().equals(userId)) {
            throw new BffProblems.MediaNotOwnerProblem(mediaId.toString());
        }
        repository.delete(asset);
        if (repository.countByObjectKey(asset.objectKey()) == 0) {
            try {
                storage.delete(asset.objectKey());
            } catch (RuntimeException e) {
                // L'objet reste orphelin dans le bucket : sans gravité (clé immuable, pas de référence).
                logger.warn("Objet média orphelin non supprimé : {}", asset.objectKey(), e);
            }
        }
    }

    /** Supprime toutes les images personnelles d'un compte (les objets partagés restent). */
    @org.springframework.transaction.annotation.Transactional
    public void deleteAllForUser(String userId) {
        for (MediaAsset asset : repository.findByUploadedBy(userId)) {
            repository.delete(asset);
            if (repository.countByObjectKey(asset.objectKey()) == 0) {
                try {
                    storage.delete(asset.objectKey());
                } catch (RuntimeException e) {
                    logger.warn("Objet média orphelin non supprimé : {}", asset.objectKey(), e);
                }
            }
        }
    }

    private String urlFor(String objectKey) {
        return MediaPublicUrl.urlFor(properties, objectKey);
    }

    private static MediaView toView(MediaAsset asset) {
        return new MediaView(asset.id(), asset.url(), asset.contentType(), asset.sizeBytes(),
                asset.width(), asset.height());
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
