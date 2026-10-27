package com.joutio.bff.infrastructure.out.media;

import com.joutio.bff.application.MediaObject;
import com.joutio.bff.application.MediaObjectPage;
import com.joutio.bff.application.MediaStorage;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.List;
import java.util.Map;

/**
 * Adaptateur S3/MinIO : dépôt immuable des images normalisées (clé dérivée du sha256, donc un
 * même contenu écrase le même objet) avec `Cache-Control` long et métadonnée `sha256` (audit).
 */
@Component
public class S3MediaStorageAdapter implements MediaStorage {

    private static final Logger logger = LoggerFactory.getLogger(S3MediaStorageAdapter.class);

    private final S3Client client;
    private final MediaProperties properties;

    public S3MediaStorageAdapter(S3Client client, MediaProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType, String sha256) {
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket())
                            .key(objectKey)
                            .contentType(contentType)
                            .cacheControl("public, max-age=31536000, immutable")
                            .metadata(Map.of("sha256", sha256))
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (RuntimeException e) {
            logger.error("Échec du dépôt média {} dans {}", objectKey, bucket(), e);
            throw new BffProblems.MediaStorageUnavailableProblem("dépôt de l'image impossible");
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket())
                    .key(objectKey)
                    .build());
        } catch (S3Exception e) {
            logger.error("Échec de la suppression média {} dans {}", objectKey, bucket(), e);
            throw new BffProblems.MediaStorageUnavailableProblem("suppression de l'image impossible");
        }
    }

    @Override
    public MediaObjectPage listObjects(String prefix, String continuationToken, int maxKeys) {
        try {
            ListObjectsV2Request.Builder request = ListObjectsV2Request.builder()
                    .bucket(bucket())
                    .maxKeys(maxKeys);
            if (prefix != null && !prefix.isBlank()) {
                request.prefix(prefix);
            }
            if (continuationToken != null && !continuationToken.isBlank()) {
                request.continuationToken(continuationToken);
            }
            ListObjectsV2Response response = client.listObjectsV2(request.build());
            List<MediaObject> objects = response.contents().stream()
                    .map(summary -> new MediaObject(summary.key(), summary.size(), summary.lastModified(),
                            summary.eTag()))
                    .toList();
            return new MediaObjectPage(objects, response.nextContinuationToken());
        } catch (RuntimeException e) {
            logger.error("Échec du listing des objets média dans {}", bucket(), e);
            throw new BffProblems.MediaStorageUnavailableProblem("listing du stockage impossible");
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket())
                    .key(objectKey)
                    .build());
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            logger.error("Échec de la vérification de l'objet média {} dans {}", objectKey, bucket(), e);
            throw new BffProblems.MediaStorageUnavailableProblem("vérification de l'image impossible");
        }
    }

    private String bucket() {
        String bucket = properties.s3().bucket();
        if (bucket == null || bucket.isBlank()) {
            throw new BffProblems.MediaStorageUnavailableProblem("bucket média non configuré");
        }
        return bucket;
    }
}
