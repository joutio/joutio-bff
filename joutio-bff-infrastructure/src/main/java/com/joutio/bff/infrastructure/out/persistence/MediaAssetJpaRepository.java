package com.joutio.bff.infrastructure.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaAssetJpaRepository extends JpaRepository<MediaAssetEntity, UUID> {

    Optional<MediaAssetEntity> findBySha256AndUploadedBy(String sha256, String uploadedBy);

    long countByUploadedBy(String uploadedBy);

    List<MediaAssetEntity> findByUploadedBy(String uploadedBy);

    @Query("select coalesce(sum(asset.sizeBytes), 0) from MediaAssetEntity asset where asset.uploadedBy = :uploadedBy")
    long sumBytesByUploadedBy(@Param("uploadedBy") String uploadedBy);

    long countByObjectKey(String objectKey);

    List<MediaAssetEntity> findByObjectKey(String objectKey);

    List<MediaAssetEntity> findByObjectKeyIn(Collection<String> objectKeys);

    List<MediaAssetEntity> findAllByOrderByCreatedAtAsc();

    long deleteByObjectKey(String objectKey);

    @Query("""
            select asset.uploadedBy, count(asset), coalesce(sum(asset.sizeBytes), 0)
            from MediaAssetEntity asset
            group by asset.uploadedBy
            order by coalesce(sum(asset.sizeBytes), 0) desc, asset.uploadedBy asc
            """)
    List<Object[]> usageByUploader(Pageable pageable);

    @Query("select count(distinct asset.uploadedBy) from MediaAssetEntity asset")
    long countUploaders();
}
