package com.joutio.bff.infrastructure.out.persistence;

import com.joutio.bff.application.MediaAsset;
import com.joutio.bff.application.MediaAssetRepository;
import com.joutio.bff.application.UploaderUsage;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adaptateur JPA du catalogue média (URLs immuables, quota et déduplication par sha256). */
@Component
public class MediaAssetRepositoryAdapter implements MediaAssetRepository {

    private final MediaAssetJpaRepository repository;

    public MediaAssetRepositoryAdapter(MediaAssetJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public MediaAsset save(MediaAsset asset) {
        repository.save(toEntity(asset));
        return asset;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MediaAsset> findById(UUID id) {
        return repository.findById(id).map(MediaAssetRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MediaAsset> findBySha256AndUploadedBy(String sha256, String uploadedBy) {
        return repository.findBySha256AndUploadedBy(sha256, uploadedBy)
                .map(MediaAssetRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByUploadedBy(String uploadedBy) {
        return repository.countByUploadedBy(uploadedBy);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaAsset> findByUploadedBy(String uploadedBy) {
        return repository.findByUploadedBy(uploadedBy).stream()
                .map(MediaAssetRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long sumBytesByUploadedBy(String uploadedBy) {
        return repository.sumBytesByUploadedBy(uploadedBy);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByObjectKey(String objectKey) {
        return repository.countByObjectKey(objectKey);
    }

    @Override
    @Transactional
    public void delete(MediaAsset asset) {
        repository.deleteById(asset.id());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaAsset> findByObjectKey(String objectKey) {
        return repository.findByObjectKey(objectKey).stream()
                .map(MediaAssetRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaAsset> findByObjectKeys(Collection<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return List.of();
        }
        return repository.findByObjectKeyIn(objectKeys).stream()
                .map(MediaAssetRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaAsset> findAllOrdered() {
        return repository.findAllByOrderByCreatedAtAsc().stream()
                .map(MediaAssetRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UploaderUsage> usageByUploader(int page, int size) {
        return repository.usageByUploader(PageRequest.of(page, size)).stream()
                .map(row -> new UploaderUsage((String) row[0], ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countUploaders() {
        return repository.countUploaders();
    }

    @Override
    @Transactional
    public long deleteByObjectKey(String objectKey) {
        return repository.deleteByObjectKey(objectKey);
    }

    private static MediaAssetEntity toEntity(MediaAsset asset) {
        return new MediaAssetEntity(asset.id(), asset.objectKey(), asset.url(), asset.contentType(),
                asset.sizeBytes(), asset.width(), asset.height(), asset.sha256(), asset.uploadedBy(),
                asset.createdAt());
    }

    private static MediaAsset toDomain(MediaAssetEntity entity) {
        return new MediaAsset(entity.getId(), entity.getObjectKey(), entity.getUrl(),
                entity.getContentType(), entity.getSizeBytes(), entity.getWidth(), entity.getHeight(),
                entity.getSha256(), entity.getUploadedBy(), entity.getCreatedAt());
    }
}
