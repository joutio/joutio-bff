package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.response.MediaView;
import com.joutio.bff.infrastructure.out.media.MediaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MediaServiceTest {

    private static final String USER_ID = "user-1";
    private static final String PUBLIC_BASE = "https://media.example.test/joutio-media/";

    private final MediaStorage storage = mock(MediaStorage.class);
    private final MediaAssetRepository repository = mock(MediaAssetRepository.class);
    private final MediaService service;

    MediaServiceTest() {
        MediaProperties properties = new MediaProperties(
                PUBLIC_BASE,
                new MediaProperties.S3("http://localhost:9000", "us-east-1", "joutio-media", "key", "secret", true),
                DataSize.ofMegabytes(1),
                100,
                8000,
                40_000_000L,
                0.85,
                2,
                DataSize.ofKilobytes(10));
        service = new MediaService(properties, new MediaImageNormalizer(properties), storage, repository);
    }

    @BeforeEach
    void allowedQuota() {
        when(repository.findBySha256AndUploadedBy(anyString(), eq(USER_ID))).thenReturn(Optional.empty());
        when(repository.countByUploadedBy(USER_ID)).thenReturn(0L);
        when(repository.sumBytesByUploadedBy(USER_ID)).thenReturn(0L);
    }

    @Test
    void uploadsNormalizedImageAndReturnsPublicUrl() throws IOException {
        MediaView view = service.upload(USER_ID, file(image(400, 200)));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), any(), eq("image/jpeg"), anyString());
        assertThat(key.getValue()).startsWith("uploads/").endsWith(".jpg");
        assertThat(view.url()).isEqualTo(PUBLIC_BASE + key.getValue());
        assertThat(view.contentType()).isEqualTo("image/jpeg");
        assertThat(view.width()).isEqualTo(100);
        assertThat(view.height()).isEqualTo(50);
        verify(repository).save(any(MediaAsset.class));
    }

    @Test
    void deduplicatesIdenticalContentForSameUser() throws IOException {
        MediaAsset existing = new MediaAsset(UUID.randomUUID(), "uploads/abc.jpg",
                PUBLIC_BASE + "uploads/abc.jpg", "image/jpeg", 100, 100, 50, "sha", USER_ID, Instant.now());
        when(repository.findBySha256AndUploadedBy(anyString(), eq(USER_ID))).thenReturn(Optional.of(existing));

        MediaView view = service.upload(USER_ID, file(image(20, 20)));

        assertThat(view.id()).isEqualTo(existing.id());
        verifyNoInteractions(storage);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> service.upload(USER_ID,
                new MockMultipartFile("file", "cover.png", "image/png", new byte[0])))
                .isInstanceOf(BffProblems.MediaFileMissingProblem.class);
    }

    @Test
    void rejectsFileBeyondMaxSize() {
        byte[] tooBig = new byte[(int) DataSize.ofMegabytes(1).toBytes() + 1];

        assertThatThrownBy(() -> service.upload(USER_ID,
                new MockMultipartFile("file", "cover.png", "image/png", tooBig)))
                .isInstanceOf(BffProblems.MediaTooLargeProblem.class);
    }

    @Test
    void rejectsWhenAssetCountQuotaReached() throws IOException {
        when(repository.countByUploadedBy(USER_ID)).thenReturn(2L);

        assertThatThrownBy(() -> service.upload(USER_ID, file(image(20, 20))))
                .isInstanceOf(BffProblems.MediaQuotaExceededProblem.class);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsWhenByteQuotaReached() throws IOException {
        when(repository.sumBytesByUploadedBy(USER_ID)).thenReturn(DataSize.ofKilobytes(10).toBytes());

        assertThatThrownBy(() -> service.upload(USER_ID, file(image(20, 20))))
                .isInstanceOf(BffProblems.MediaQuotaExceededProblem.class);
    }

    @Test
    void deletesOwnedAssetAndObjectWhenUnreferenced() {
        MediaAsset asset = asset("uploads/abc.jpg");
        when(repository.findById(asset.id())).thenReturn(Optional.of(asset));
        when(repository.countByObjectKey(asset.objectKey())).thenReturn(0L);

        service.delete(USER_ID, asset.id());

        verify(repository).delete(asset);
        verify(storage).delete(asset.objectKey());
    }

    @Test
    void keepsSharedObjectWhenAnotherAssetReferencesIt() {
        MediaAsset asset = asset("uploads/shared.jpg");
        when(repository.findById(asset.id())).thenReturn(Optional.of(asset));
        when(repository.countByObjectKey(asset.objectKey())).thenReturn(1L);

        service.delete(USER_ID, asset.id());

        verify(repository).delete(asset);
        verify(storage, never()).delete(anyString());
    }

    @Test
    void rejectsDeletionOfAnotherUsersAsset() {
        MediaAsset asset = asset("uploads/abc.jpg");
        when(repository.findById(asset.id())).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> service.delete("user-2", asset.id()))
                .isInstanceOf(BffProblems.MediaNotOwnerProblem.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void rejectsDeletionOfUnknownAsset() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER_ID, id))
                .isInstanceOf(BffProblems.MediaNotFoundProblem.class);
    }

    private static MediaAsset asset(String objectKey) {
        return new MediaAsset(UUID.randomUUID(), objectKey, PUBLIC_BASE + objectKey, "image/jpeg",
                100, 100, 50, "sha", USER_ID, Instant.now());
    }

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "cover.png", "image/png", content);
    }

    private static byte[] image(int width, int height) throws IOException {
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(buffered, "png", bytes);
        return bytes.toByteArray();
    }
}
