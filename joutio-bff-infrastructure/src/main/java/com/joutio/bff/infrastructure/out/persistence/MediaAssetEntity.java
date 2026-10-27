package com.joutio.bff.infrastructure.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Référence d'une image stockée dans MinIO (table {@code media_asset}). */
@Entity
@Table(name = "media_asset")
public class MediaAssetEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "url", nullable = false, length = 1024)
    private String url;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "uploaded_by", nullable = false, length = 64)
    private String uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MediaAssetEntity() {
    }

    public MediaAssetEntity(UUID id, String objectKey, String url, String contentType, long sizeBytes,
                            int width, int height, String sha256, String uploadedBy, Instant createdAt) {
        this.id = id;
        this.objectKey = objectKey;
        this.url = url;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
        this.sha256 = sha256;
        this.uploadedBy = uploadedBy;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getUrl() {
        return url;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getSha256() {
        return sha256;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
